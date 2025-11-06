package io.github.jdeeplearn.rag.repository;

import com.couchbase.client.core.error.CouchbaseException;
import com.couchbase.client.core.error.DurabilityImpossibleException;
import com.couchbase.client.core.msg.kv.DurabilityLevel;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.json.JsonArray;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.kv.InsertOptions;
import io.github.jdeeplearn.rag.model.FaqDocument;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.util.Locale;
import java.util.Objects;

/**
 * Repository for persisting FAQ documents to Couchbase.
 * <p>
 * Uses DurabilityLevel from the official 3.x Java SDK:
 * NONE, MAJORITY, MAJORITY_AND_PERSIST_TO_ACTIVE, PERSIST_TO_MAJORITY.
 * <p>
 * For local / single-node clusters, use NONE to avoid DurabilityImpossibleException.
 */
@Repository
public class CouchbaseFaqRepository {

    private static final Logger log = LogManager.getLogger(CouchbaseFaqRepository.class);

    private final Collection collection;
    private final DurabilityLevel durabilityLevel;

    public CouchbaseFaqRepository(
            Collection collection,
            @Value("${couchbase.durability:none}") String durabilitySetting
    ) {
        this.collection = Objects.requireNonNull(collection, "collection must not be null");
        this.durabilityLevel = mapDurability(durabilitySetting);

        log.info("Initialized CouchbaseFaqRepository with durability={}", this.durabilityLevel);
    }

    private DurabilityLevel mapDurability(String value) {
        if (value == null) {
            return DurabilityLevel.NONE;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "majority" -> DurabilityLevel.MAJORITY;
            case "majority_and_persist_to_active" -> DurabilityLevel.MAJORITY_AND_PERSIST_TO_ACTIVE;
            case "persist_to_majority" -> DurabilityLevel.PERSIST_TO_MAJORITY;
            default -> DurabilityLevel.NONE;
        };
    }

    /**
     * Insert a single FAQ document with enhanced durability if possible.
     * Falls back to DurabilityLevel.NONE if the cluster cannot satisfy the requested level.
     */
    public void insertFaq(FaqDocument doc) {
        if (doc == null) {
            log.warn("Skipped insert for null FAQ document");
            return;
        }

        JsonArray vectorArray = JsonArray.create();

        for (var v : doc.getQuestionVector()) {
            vectorArray.add(v);
        }

        JsonObject content = JsonObject.create()
                .put("type", doc.getType())
                .put("category", doc.getCategory())
                .put("question", doc.getQuestion())
                .put("answer", doc.getAnswer())
                .put("image", doc.getImage())
                .put("link", doc.getLink())
                .put("question_vector", vectorArray)
                .put("meta", JsonObject.from(doc.getMeta()));

        try {
            collection.insert(
                    doc.getId(),
                    content,
                    InsertOptions.insertOptions()
                            .durability(durabilityLevel)
            );
            log.info("Inserted FAQ [{}] with durability={}", doc.getId(), durabilityLevel);

        } catch (DurabilityImpossibleException e) {
            // Typical on single-node dev/local clusters when durability != NONE
            log.warn("DurabilityImpossible for id='{}' (requested={}), retrying with DurabilityLevel.NONE",
                    doc.getId(), durabilityLevel);
            retryInsertWithoutDurability(doc, content);

        } catch (CouchbaseException e) {
            log.error("Couchbase error inserting [{}]: {}", doc.getId(), e.toString(), e);

        } catch (Exception e) {
            log.error("Unexpected error inserting [{}]: {}", doc.getId(), e.toString(), e);
        }
    }

    private void retryInsertWithoutDurability(FaqDocument doc, JsonObject content) {
        try {
            collection.insert(
                    doc.getId(),
                    content,
                    InsertOptions.insertOptions()
                            .durability(DurabilityLevel.NONE)
            );
            log.info("Re-inserted FAQ [{}] with durability=NONE", doc.getId());
        } catch (Exception ex) {
            log.error("Retry insert failed for [{}]: {}", doc.getId(), ex.toString(), ex);
        }
    }
}
