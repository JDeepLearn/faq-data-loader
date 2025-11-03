package io.github.jdeeplearn.rag.infra;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;

/**
 * Ensures the Couchbase FTS (Full Text Search) vector index exists.
 *
 * - Index name: faq_vectors
 * - Bucket:      faq_bucket
 * - Scope:       faq_scope
 * - Collection:  faqs
 * - Vector field: question_vector (dims = embedding.dim, similarity = cosine by default)
 *
 * This uses the Couchbase FTS REST API on port 8094.
 * The mapping is intentionally minimal and known to be accepted by Couchbase 8.x:
 *
 * {
 *   "type": "fulltext-index",
 *   "name": "faq_vectors",
 *   "uuid": "",
 *   "sourceType": "couchbase",
 *   "sourceName": "faq_bucket",
 *   "sourceParams": {},
 *   "planParams": {},
 *   "params": {
 *     "doc_config": {
 *       "mode": "scope.collection.type_field",
 *       "type_field": "type"
 *     },
 *     "mapping": {
 *       "default_analyzer": "standard",
 *       "default_field": "_all",
 *       "default_mapping": { "enabled": false },
 *       "index_dynamic": true,
 *       "store_dynamic": false,
 *       "types": {
 *         "faq_scope.faqs": {
 *           "enabled": true,
 *           "dynamic": true,
 *           "properties": {
 *             "question_vector": {
 *               "enabled": true,
 *               "dynamic": false,
 *               "fields": [
 *                 {
 *                   "name": "question_vector",
 *                   "type": "vector",
 *                   "dims": 1024,
 *                   "similarity": "cosine"
 *                 }
 *               ]
 *             }
 *           }
 *         }
 *       }
 *     }
 *   }
 * }
 */
@Component
public class VectorSearchIndexManager implements InitializingBean {

    private static final Logger log = LogManager.getLogger(VectorSearchIndexManager.class);

    private final WebClient client;
    private final String indexName;
    private final String bucket;
    private final String scope;
    private final String collection;
    private final int dims;
    private final String similarity;
    private final boolean ensureIndex;

    public VectorSearchIndexManager(
            @Value("${cb.search.host:localhost}") String host,
            @Value("${cb.search.port:8094}") int port,
            @Value("${spring.data.couchbase.bucket-name}") String bucket,
            @Value("${spring.data.couchbase.scope-name}") String scope,
            @Value("${uploader.collection}") String collection,
            @Value("${spring.couchbase.username}") String username,
            @Value("${spring.couchbase.password}") String password,
            @Value("${cb.vector.index-name:faq_vectors}") String indexName,
            @Value("${embedding.dim:1024}") int dims,
            @Value("${cb.vector.similarity:cosine}") String similarity,
            @Value("${cb.vector.ensure-index:true}") boolean ensureIndex
    ) {
        this.indexName = indexName;
        this.bucket = bucket;
        this.scope = scope;
        this.collection = collection;
        this.dims = dims;
        this.similarity = similarity;
        this.ensureIndex = ensureIndex;

        this.client = WebClient.builder()
                .baseUrl("http://" + host + ":" + port)
                .defaultHeaders(h -> h.setBasicAuth(username, password))
                .build();
    }

    @Override
    public void afterPropertiesSet() {
        if (!ensureIndex) {
            log.info("Vector index creation disabled (cb.vector.ensure-index=false)");
            return;
        }

        try {
            if (indexExists()) {
                log.info("FTS vector index '{}' already exists.", indexName);
            } else {
                createVectorIndex();
                log.info("FTS vector index '{}' created successfully (dims={}, sim={}).",
                        indexName, dims, similarity);
            }
        } catch (Exception e) {
            log.error("Failed to ensure vector index '{}': {}", indexName, e.getMessage());
        }
    }

    /**
     * Checks if the index already exists by listing index definitions.
     */
    @SuppressWarnings("unchecked")
    private boolean indexExists() {
        try {
            var response = client.get()
                    .uri("/api/index")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null) {
                return false;
            }
            Object defs = response.get("indexDefs");
            if (defs instanceof Map<?, ?> map) {
                return map.containsKey(indexName);
            }
        } catch (Exception e) {
            log.warn("Unable to determine if index '{}' exists: {}", indexName, e.getMessage());
        }
        return false;
    }

    /**
     * Creates the vector index using the minimal, known-good mapping for Couchbase 8.x.
     */
    private void createVectorIndex() {
        log.info("Creating vector index '{}' for {}/{}/{}", indexName, bucket, scope, collection);

        // Minimal and valid FTS vector index definition
        var body = Map.of(
                "type", "fulltext-index",
                "name", indexName,
                "uuid", "",
                "sourceType", "couchbase",
                "sourceName", bucket,
                "sourceParams", Map.of(),
                "planParams", Map.of(),
                "params", Map.of(
                        "doc_config", Map.of(
                                "mode", "scope.collection.type_field",
                                "type_field", "type"
                        ),
                        "mapping", Map.of(
                                "default_analyzer", "standard",
                                "default_field", "_all",
                                "default_mapping", Map.of("enabled", false),
                                "index_dynamic", true,
                                "store_dynamic", false,
                                "types", Map.of(
                                        scope + "." + collection, Map.of(
                                                "enabled", true,
                                                "dynamic", true,
                                                "properties", Map.of(
                                                        "question_vector", Map.of(
                                                                "enabled", true,
                                                                "dynamic", false,
                                                                "fields", List.of(
                                                                        Map.of(
                                                                                "name", "question_vector",
                                                                                "type", "vector",
                                                                                "dims", dims,
                                                                                "similarity", similarity
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );

        try {
            var response = client.put()
                    .uri("/api/index/{name}", indexName)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .toBodilessEntity()
                    .block();

            if (response != null && response.getStatusCode().is2xxSuccessful()) {
                log.info("FTS vector index '{}' created successfully (dims={}, sim={}).",
                        indexName, dims, similarity);
            } else {
                log.warn("FTS vector index '{}' may already exist or returned non-2xx status: {}",
                        indexName,
                        response == null ? "no response" : response.getStatusCode());
            }
        } catch (WebClientResponseException.BadRequest e) {
            String bodyText = e.getResponseBodyAsString();
            if (bodyText.contains("already exists")) {
                log.info("FTS vector index '{}' already exists — skipping creation.", indexName);
            } else {
                log.error("FTS index creation returned 400 Bad Request: {}", bodyText);
            }
        } catch (Exception e) {
            log.error("Unexpected error while creating FTS index '{}': {}", indexName, e.toString());
        }
    }
}