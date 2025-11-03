package io.github.jdeeplearn.rag.infra;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.Scope;
import com.couchbase.client.java.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.time.Duration;

@Repository
public class CouchbaseFaqRepository {

    private static final Logger log = LogManager.getLogger(CouchbaseFaqRepository.class);

    private final Collection collection;

    public CouchbaseFaqRepository(
            Bucket bucket,
            @Value("${spring.data.couchbase.scope-name}") String scopeName,
            @Value("${uploader.collection}") String collectionName) {

        bucket.waitUntilReady(Duration.ofSeconds(30));
        Scope scope = bucket.scope(scopeName);
        this.collection = scope.collection(collectionName);

        log.info("Initialized repository for scope='{}', collection='{}'", scopeName, collectionName);
    }

    /** Idempotent write used for refreshing vectors on reruns. */
    public void upsert(String id, JsonObject doc) {
        collection.upsert(id, doc);
    }

    /** Legacy insert (kept if you still need it somewhere). */
    public void insert(String id, JsonObject doc) {
        collection.insert(id, doc);
    }
}

