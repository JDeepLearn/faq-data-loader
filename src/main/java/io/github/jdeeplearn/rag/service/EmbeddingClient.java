package io.github.jdeeplearn.rag.service;

import io.github.jdeeplearn.rag.model.EmbeddingRequest;
import io.github.jdeeplearn.rag.model.EmbeddingResponse;
import io.github.jdeeplearn.rag.model.EmbeddingResponse.EmbeddingItem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * Embedding client for Granite-compatible /embed API.
 * Uses strongly-typed POJOs for request/response.
 */
@Component
public class EmbeddingClient {

    private static final Logger log = LogManager.getLogger(EmbeddingClient.class);

    private final WebClient webClient;
    private final String modelName;
    private final String provider;
    private final int timeoutMs;

    public EmbeddingClient(
            @Value("${embedding.service-url:http://localhost:8000/}") String baseUrl,
            @Value("${embedding.model-name:granite-embedding-english-r2}") String modelName,
            @Value("${embedding.provider:ibm-granite}") String provider,
            @Value("${embedding.timeout-ms:5000}") int timeoutMs
    ) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
        this.modelName = modelName;
        this.provider = provider;
        this.timeoutMs = timeoutMs;
    }

    /**
     * Generates an embedding vector for a single text input.
     */
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            log.warn("Skipping embedding for blank text");
            return new float[0];
        }

        try {
            EmbeddingRequest request = EmbeddingRequest.of(text);

            EmbeddingResponse response = webClient.post()
                    .uri("/embed")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(EmbeddingResponse.class)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .block();

            if (response == null || response.getEmbeddings() == null || response.getEmbeddings().isEmpty()) {
                throw new IllegalStateException("No embeddings returned from service");
            }

            EmbeddingItem item = response.getEmbeddings().get(0);
            List<Double> values = item.getVector();
            if (values == null || values.isEmpty()) {
                throw new IllegalStateException("Empty embedding vector");
            }

            float[] vector = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                vector[i] = values.get(i).floatValue();
            }

            log.info("Embedding success: model={}, dim={}, len={}",
                    response.getModel(), response.getEmbeddingDim(), vector.length);

            return vector;

        } catch (WebClientResponseException e) {
            log.error("Embedding service HTTP error: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Embedding service call failed: {}", e.toString(), e);
        }
        return new float[0];
    }

    public String getModelName() {
        return modelName;
    }

    public String getProvider() {
        return provider;
    }
}