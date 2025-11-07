package io.github.jdeeplearn.rag.model;

import java.util.List;

/**
 * Request payload for the embedding API.
 * Example:
 * {
 *   "inputs": ["text1", "text2"]
 * }
 */
public record EmbeddingRequest(List<String> inputs) {

    public static EmbeddingRequest of(String text) {
        return new EmbeddingRequest(List.of(text));
    }

    public static EmbeddingRequest of(List<String> texts) {
        return new EmbeddingRequest(texts);
    }
}
