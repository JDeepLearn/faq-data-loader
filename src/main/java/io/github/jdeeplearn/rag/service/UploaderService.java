package io.github.jdeeplearn.rag.service;

import com.couchbase.client.core.error.CouchbaseException;
import com.couchbase.client.core.error.DocumentExistsException;
import com.couchbase.client.java.Cluster;
import io.github.jdeeplearn.rag.client.EmbeddingClient;
import io.github.jdeeplearn.rag.data.Faq;
import io.github.jdeeplearn.rag.data.FaqDocumentMapper;
import io.github.jdeeplearn.rag.infra.CouchbaseFaqRepository;
import io.github.jdeeplearn.rag.io.FaqFileReader;
import org.apache.logging.log4j.CloseableThreadContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UploaderService implements CommandLineRunner {

    private static final Logger log = LogManager.getLogger(UploaderService.class);

    private final Cluster cluster;
    private final FaqFileReader reader;
    private final FaqDocumentMapper mapper;
    private final CouchbaseFaqRepository repo;
    private final EmbeddingClient embeddingClient;
    private final boolean failOpen;

    public UploaderService(Cluster cluster,
                           FaqFileReader reader,
                           FaqDocumentMapper mapper,
                           CouchbaseFaqRepository repo,
                           EmbeddingClient embeddingClient,
                           @Value("${embedding.fail-open:false}") boolean failOpen) {
        this.cluster = cluster;
        this.reader = reader;
        this.mapper = mapper;
        this.repo = repo;
        this.embeddingClient = embeddingClient;
        this.failOpen = failOpen;
    }

    @Override
    public void run(String... args) {
        log.info("=== Couchbase FAQ Uploader starting (with embeddings) ===");
        try {
            log.info("Cluster ping: {}", cluster.ping());

            List<Faq> faqs = reader.readAll();
            if (faqs.isEmpty()) {
                log.warn("No FAQs to upload; exiting.");
                return;
            }

            int upserts = 0, skipped = 0, fail = 0;

            for (Faq faq : faqs) {
                final String id = mapper.generateId(faq);

                try (CloseableThreadContext.Instance ctx =
                             CloseableThreadContext.put("faqId", id)) {

                    // 1) get embedding for the question
                    List<Double> vector = null;
                    try {
                        vector = embeddingClient.embed(faq.question());
                    } catch (Exception e) {
                        if (failOpen) {
                            log.warn("Embedding failed; proceeding without vector: {}", e.toString());
                        } else {
                            throw e;
                        }
                    }

                    // 2) map to document with optional vector
                    var doc = mapper.toDocument(id, faq, vector);

                    // 3) upsert (idempotent)
                    repo.upsert(id, doc);
                    log.info("Upserted");
                    upserts++;
                } catch (DocumentExistsException exists) {
                    // (won't normally happen with upsert, but kept for completeness)
                    log.debug("Duplicate detected; skipping insert");
                    skipped++;
                } catch (CouchbaseException cbx) {
                    log.error("Couchbase exception: {}", cbx.toString());
                    fail++;
                } catch (Exception e) {
                    log.error("Unexpected exception: {}", e.getMessage(), e);
                    fail++;
                }
            }

            log.info("=== Upload Complete ===");
            log.info("Upserts: {}, Skipped: {}, Failures: {}", upserts, skipped, fail);

        } catch (Exception fatal) {
            log.fatal("Uploader failed fatally", fatal);
        }
    }
}
