package io.github.jdeeplearn.rag.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * EmbeddingClient
 *
 * Calls an external embedding service and returns float[] vectors.
 *
 * Production considerations:
 *  - Timeouts configured via reactor-netty.
 *  - Model and provider configurable via properties.
 *  - Dimension mismatch logged and monitored.
 */
@Component
public class EmbeddingClient {

    private static final Logger log = LogManager.getLogger(EmbeddingClient.class);

    private final WebClient webClient;
    private final int expectedDim;
    private final String modelName;
    private final String provider;
    private final Duration requestTimeout;

    public EmbeddingClient(
            @Value("${embedding.service-url:http://localhost:8000/embed}") String serviceUrl,
            @Value("${embedding.dim:1024}") int expectedDim,
            @Value("${embedding.model-name:e5-large-v2}") String modelName,
            @Value("${embedding.provider:intfloat}") String provider,
            @Value("${embedding.timeout-ms:5000}") long timeoutMs
    ) {
        this.expectedDim = expectedDim;
        this.modelName = modelName;
        this.provider = provider;
        this.requestTimeout = Duration.ofMillis(timeoutMs);

        HttpClient httpClient = HttpClient.create()
                .responseTimeout(this.requestTimeout);

        this.webClient = WebClient.builder()
                .baseUrl(serviceUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();

        log.info("EmbeddingClient initialized (provider={}, model={}, dim={}, url={}, timeoutMs={})",
                provider, modelName, expectedDim, serviceUrl, timeoutMs);
    }

    /**
     * Generate an embedding vector for the given text.
     *
     * @param text input text (typically the FAQ question)
     * @return float[] embedding vector
     */
    public float[] embed(String text) {
        Objects.requireNonNull(text, "text must not be null");
        long start = System.nanoTime();

        try {
            EmbeddingResponse response = webClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new EmbeddingRequest(modelName, provider, text))
                    .retrieve()
                    .bodyToMono(EmbeddingResponse.class)
                    .timeout(requestTimeout)
                    .block();

            if (response == null || response.embedding == null || response.embedding.isEmpty()) {
                throw new IllegalStateException("Empty embedding returned for model=" + modelName);
            }

            float[] vector = new float[response.embedding.size()];
            for (int i = 0; i < response.embedding.size(); i++) {
                vector[i] = response.embedding.get(i).floatValue();
            }

            if (vector.length != expectedDim) {
                log.warn("Embedding dimension mismatch for model='{}': expected={}, got={}",
                        modelName, expectedDim, vector.length);
            }

            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            log.debug("Embedding generated in {} ms using model={}", elapsedMs, modelName);

            return vector;

        } catch (WebClientResponseException e) {
            log.error("Embedding service HTTP error (status={}): {}",
                    e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new RuntimeException("Embedding service HTTP error: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Embedding service error: {}", e.toString(), e);
            throw new RuntimeException("Embedding service error: " + e.getMessage(), e);
        }
    }

    /**
     * Request body sent to the embedding service.
     * Adjust field names if your service uses a different contract.
     */
    private record EmbeddingRequest(String model, String provider, String text) {}

    /**
     * Response expected from the embedding service.
     * Example JSON: { "embedding": [0.1, 0.2, ...] }
     */
    private record EmbeddingResponse(List<Double> embedding) {}

    public String getModelName() {
        return modelName;
    }

    public String getProvider() {
        return provider;
    }

    public int getExpectedDim() {
        return expectedDim;
    }
}