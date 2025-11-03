package io.github.jdeeplearn.rag.service;

import com.couchbase.client.core.error.DocumentNotFoundException;
import com.couchbase.client.java.*;
import com.couchbase.client.java.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deterministic KV-only E2E test:
 * - Writes to _default._default
 * - No N1QL / no indexes (no rebalance race)
 * - Polls briefly for KV visibility
 */
class FaqUploaderIntegrationTest extends BaseIntegrationTest {

    @Autowired UploaderService uploader;
    @Autowired Cluster cluster;

    private static final String BUCKET = CouchbaseContainerConfig.BUCKET;

    @Test
    void shouldUploadFaqsToCouchbaseSuccessfully() throws Exception {
        // Run the same logic your app executes at startup
        uploader.run();

        // Compute the exact deterministic ID your app generates
        String question = "How do I reset my password?";
        byte[] sha1 = MessageDigest.getInstance("SHA-1").digest(question.getBytes());
        String id = "faq-" + HexFormat.of().formatHex(sha1, 0, 4);

        Bucket bucket = cluster.bucket(BUCKET);
        bucket.waitUntilReady(Duration.ofSeconds(10));
        Collection collection = bucket.scope("faq_scope").collection("faqs"); // _default._default

        // Poll up to ~5s until the doc is visible
        JsonObject doc = null;
        for (int i = 0; i < 10; i++) {
            try {
                doc = collection.get(id).contentAsObject();
                break;
            } catch (DocumentNotFoundException e) {
                Thread.sleep(500);
            }
        }

        assertThat(doc).withFailMessage("Expected %s to exist in _default._default", id).isNotNull();
        assertThat(doc.getString("type")).isEqualTo("faq");
        assertThat(doc.getString("question")).containsIgnoringCase("reset my password");
    }
}
