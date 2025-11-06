package io.github.jdeeplearn.rag.index;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.ExchangeFilterFunctions;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
public class VectorSearchIndexManager {

    private static final Logger log = LogManager.getLogger(VectorSearchIndexManager.class);

    private final WebClient webClient;
    private final String indexName;
    private final String bucketName;
    private final String scopeName;
    private final String collectionName;
    private final int embeddingDim;

    public VectorSearchIndexManager(
            @Value("${couchbase.fts.url:http://localhost:8094}") String ftsUrl,
            @Value("${spring.data.couchbase.bucket-name:faq_bucket}") String bucketName,
            @Value("${spring.data.couchbase.scope-name:faq_scope}") String scopeName,
            @Value("${uploader.collection:faqs}") String collectionName,
            @Value("${vector.index-name:faq_vectors}") String indexName,
            @Value("${embedding.dim:1024}") int embeddingDim,
            @Value("${spring.couchbase.username:admin}") String username,
            @Value("${spring.couchbase.password:password}") String password
    ) {
        this.webClient = WebClient.builder()
                .baseUrl(ftsUrl)
                .filter(ExchangeFilterFunctions.basicAuthentication(username, password))
                .build();
        this.bucketName = bucketName;
        this.scopeName = scopeName;
        this.collectionName = collectionName;
        this.indexName = indexName;
        this.embeddingDim = embeddingDim;
    }

    public void ensureIndex() {
        String fqCollection = scopeName + "." + collectionName;
        String path = "/api/index/" + indexName;

        Map<String, Object> payload = Map.of(
                "type", "fulltext-index",
                "name", indexName,
                "sourceType", "couchbase",
                "sourceName", bucketName,
                "planParams", Map.of(
                        "maxPartitionsPerPIndex", 1024,
                        "indexPartitions", 1
                ),
                "params", Map.of(
                        "doc_config", Map.of(
                                "mode", "scope.collection.type_field",
                                "type_field", "type"
                        ),
                        "mapping", Map.of(
                                "analysis", Map.of(),
                                "default_analyzer", "standard",
                                "default_datetime_parser", "dateTimeOptional",
                                "default_field", "_all",
                                "default_mapping", Map.of(
                                        "enabled", false,
                                        "dynamic", false
                                ),
                                "index_dynamic", false,
                                "docvalues_dynamic", false,
                                "store_dynamic", false,
                                "types", Map.of(
                                        fqCollection, Map.of(
                                                "enabled", true,
                                                "dynamic", false,
                                                "properties", Map.of(
                                                        "question_vector", Map.of(
                                                                "enabled", true,
                                                                "fields", new Object[]{
                                                                        Map.of(
                                                                                "name", "question_vector",
                                                                                "type", "vector",
                                                                                "dims", embeddingDim,
                                                                                "similarity", "cosine"
                                                                        )
                                                                }
                                                        ),
                                                        "question", Map.of("enabled", true),
                                                        "answer", Map.of("enabled", true),
                                                        "category", Map.of("enabled", true),
                                                        "image", Map.of("enabled", true),
                                                        "link", Map.of("enabled", true)
                                                )
                                        )
                                )
                        ),
                        "store", Map.of(
                                "indexType", "scorch"
                        )
                ),
                "sourceParams", Map.of()
        );

        log.info("Ensuring FTS vector index '{}' with basic auth user='admin'", indexName);

        try {
            String responseBody = webClient.put()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .exchangeToMono(response -> {
                        HttpStatusCode status = response.statusCode();
                        return response.bodyToMono(String.class)
                                .flatMap(body -> {
                                    if (status.is2xxSuccessful()) {
                                        log.info("FTS index '{}' created/updated successfully", indexName);
                                        return Mono.just(body);
                                    }
                                    if (status == HttpStatus.BAD_REQUEST && body.contains("already exists")) {
                                        log.info("FTS index '{}' already exists, skipping creation.", indexName);
                                        return Mono.empty();
                                    }
                                    log.error("FTS index '{}' creation failed (status={}): {}", indexName, status, body);
                                    return Mono.error(new RuntimeException(
                                            "FTS index creation failed with status " + status + ": " + body));
                                });
                    })
                    .block();

            if (responseBody != null) {
                log.debug("ensureIndex response body: {}", responseBody);
            }

        } catch (Exception e) {
            log.error("Unexpected error ensuring FTS index '{}': {}", indexName, e.toString(), e);
        }
    }
}
