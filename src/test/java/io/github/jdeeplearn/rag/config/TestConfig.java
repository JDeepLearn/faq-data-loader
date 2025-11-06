package io.github.jdeeplearn.rag.config;

import io.github.jdeeplearn.rag.service.EmbeddingClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Test configuration providing mock dependencies.
 */
@TestConfiguration
public class TestConfig {

    @Bean
    public EmbeddingClient mockEmbeddingClient() {
        // Subclass override (since EmbeddingClient is a concrete class)
        return new EmbeddingClient("http://localhost:9999/embed", 1024, "mock-model", "mock-provider", 1000) {
            @Override
            public float[] embed(String question) {
                float[] vec = new float[1024];
                for (int i = 0; i < vec.length; i++) {
                    vec[i] = (float) Math.random() * 0.01f;
                }
                return vec;
            }
        };
    }
}
