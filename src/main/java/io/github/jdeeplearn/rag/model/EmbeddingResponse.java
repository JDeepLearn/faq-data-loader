package io.github.jdeeplearn.rag.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Response model from the embedding API.
 *
 * Example response:
 * {
 *   "model": "ibm-granite/granite-embedding-english-r2",
 *   "model_version": "1.0.0",
 *   "embedding_dim": 768,
 *   "embeddings": [
 *     { "vector": [0.0113865, ...], "text": null, "index": 1 }
 *   ],
 *   "metadata": { "request_id": "...", "num_inputs": 2, "total_chars": 58 },
 *   "generated_at": "2025-11-07T13:56:01.396092"
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EmbeddingResponse {

    private String model;
    @JsonProperty("model_version")
    private String modelVersion;
    @JsonProperty("embedding_dim")
    private int embeddingDim;
    private List<EmbeddingItem> embeddings;
    private Map<String, Object> metadata;
    @JsonProperty("generated_at")
    private String generatedAt;

    public String getModel() {
        return model;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public int getEmbeddingDim() {
        return embeddingDim;
    }

    public List<EmbeddingItem> getEmbeddings() {
        return embeddings;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public String getGeneratedAt() {
        return generatedAt;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EmbeddingItem {
        private List<Double> vector;
        private String text;
        private int index;

        public List<Double> getVector() {
            return vector;
        }

        public String getText() {
            return text;
        }

        public int getIndex() {
            return index;
        }
    }
}
