package io.github.jdeeplearn.rag.client;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Calls the external embedding service to generate a vector for input text.
 * Expects JSON:  request { "text": "..." }
 *           and response { "embedding": [float, ...] }
 */
@Component
public class EmbeddingClient {

    private static final Logger log = LogManager.getLogger(EmbeddingClient.class);

    private final WebClient webClient;
    private final String endpoint;
    private final Duration timeout;
    private final int dim;

    public EmbeddingClient(
            WebClient.Builder builder,
            @Value("${embedding.base-url}") String baseUrl,
            @Value("${embedding.endpoint}") String endpoint,
            @Value("${embedding.timeout}") Duration timeout,
            @Value("${embedding.dim}") int dim
    ) {
        this.webClient = builder
                .baseUrl(baseUrl)
                .build();
        this.endpoint = endpoint;
        this.timeout = timeout;
        this.dim = dim;
    }

    @SuppressWarnings("unchecked")
    public List<Double> embed(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Text for embedding must not be blank");
        }

        var response = webClient.post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("text", text))
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(timeout)
                .retryWhen(Retry.backoff(2, Duration.ofMillis(200)).filter(e -> true))
                .block(); // single call per upload item; safe to block in CLI batch process

        if (response == null || !response.containsKey("embedding")) {
            throw new IllegalStateException("Embedding response missing 'embedding' field");
        }

        Object v = response.get("embedding");
        if (!(v instanceof List<?> list)) {
            throw new IllegalStateException("Embedding field is not a list");
        }

        // Normalize to List<Double>, validate dimension
        var out = list.stream().map(o -> {
            if (o instanceof Number n) return n.doubleValue();
            return Double.valueOf(o.toString());
        }).toList();

        if (out.size() != dim) {
            log.warn("Embedding dimension mismatch: expected={}, got={}", dim, out.size());
        }
        return out;
    }
}
