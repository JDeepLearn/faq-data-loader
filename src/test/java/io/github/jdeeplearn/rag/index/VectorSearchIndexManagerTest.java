package io.github.jdeeplearn.rag.index;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration-style test for VectorSearchIndexManager.
 * Ensures:
 *   - Auth headers are present
 *   - Correct HTTP method and path
 *   - Handles 2xx success and 400 "already exists" responses gracefully
 */
class VectorSearchIndexManagerTest {

    private MockWebServer server;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void ensureIndexShouldCreateIndexWithBasicAuthOn2xx() throws Exception {
        // Mock successful response
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"status\":\"ok\"}")
                .addHeader("Content-Type", "application/json"));

        String baseUrl = server.url("/").toString();

        VectorSearchIndexManager manager = new VectorSearchIndexManager(
                baseUrl,
                "faq_bucket",
                "faq_scope",
                "faqs",
                "faq_vectors",
                1024,
                "admin",
                "password"
        );

        manager.ensureIndex();

        RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getMethod()).isEqualTo("PUT");
        assertThat(request.getPath()).isEqualTo("/api/index/faq_vectors");

        // Verify that Basic Auth header is present and correct
        String authHeader = request.getHeader("Authorization");
        assertThat(authHeader).isNotNull();

        String expected = "Basic " + Base64.getEncoder()
                .encodeToString("admin:password".getBytes(StandardCharsets.UTF_8));
        assertThat(authHeader).isEqualTo(expected);

        // Verify JSON body contains vector field and dims=1024
        String body = request.getBody().readUtf8();
        assertThat(body).contains("\"question_vector\"");
        assertThat(body).contains("\"dims\":1024");
    }

    @Test
    void ensureIndexShouldHandleAlreadyExists400Gracefully() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(400)
                .setBody("{\"error\":\"index already exists\"}")
                .addHeader("Content-Type", "application/json"));

        String baseUrl = server.url("/").toString();

        VectorSearchIndexManager manager = new VectorSearchIndexManager(
                baseUrl,
                "faq_bucket",
                "faq_scope",
                "faqs",
                "faq_vectors",
                1024,
                "admin",
                "password"
        );

        manager.ensureIndex();

        RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getMethod()).isEqualTo("PUT");
        assertThat(request.getPath()).isEqualTo("/api/index/faq_vectors");
    }
}
