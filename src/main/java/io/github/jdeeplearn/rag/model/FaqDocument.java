package io.github.jdeeplearn.rag.model;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable domain model representing a single FAQ entry persisted in Couchbase.
 *
 * Fields:
 *  - id: document key in Couchbase
 *  - type: logical type (e.g. "faq")
 *  - category: logical grouping (e.g. "Account", "Billing")
 *  - question / answer: curated FAQ content
 *  - image / link: optional metadata for UI
 *  - questionVector: embedding for semantic search
 *  - meta: embedding and ingestion metadata (model, provider, timestamps, etc.)
 */
public final class FaqDocument {

    private final String id;
    private final String type;
    private final String category;
    private final String question;
    private final String answer;
    private final String image;
    private final String link;
    private final float[] questionVector;
    private final Map<String, Object> meta;

    private FaqDocument(Builder builder) {
        this.id = builder.id;
        this.type = builder.type;
        this.category = builder.category;
        this.question = builder.question;
        this.answer = builder.answer;
        this.image = builder.image;
        this.link = builder.link;
        this.questionVector = builder.questionVector;
        this.meta = Collections.unmodifiableMap(new HashMap<>(builder.meta));
    }

    /**
     * Factory method used by the service layer for concise and validated creation.
     */
    public static FaqDocument of(
            String id,
            String category,
            String question,
            String answer,
            String image,
            String link,
            float[] vector,
            String provider,
            String modelName,
            int modelDim,
            String similarity,
            String source,
            String contentVersion
    ) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(question, "question must not be null");
        Objects.requireNonNull(answer, "answer must not be null");
        Objects.requireNonNull(vector, "vector must not be null");

        if (vector.length != modelDim) {
            throw new IllegalArgumentException(
                    "Embedding dimension mismatch: expected " + modelDim + ", got " + vector.length
            );
        }

        Instant now = Instant.now();

        Map<String, Object> meta = new HashMap<>();
        meta.put("provider", provider);
        meta.put("model_name", modelName);
        meta.put("model_dim", modelDim);
        meta.put("similarity", similarity);
        meta.put("source", source);
        meta.put("content_version", contentVersion);
        meta.put("created_at", now.toString());
        meta.put("indexed_at", now.toString());

        return builder()
                .id(id)
                .type("faq")
                .category(category)
                .question(question)
                .answer(answer)
                .image(image)
                .link(link)
                .questionVector(vector)
                .meta(meta)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private String type;
        private String category;
        private String question;
        private String answer;
        private String image;
        private String link;
        private float[] questionVector = new float[0];
        private Map<String, Object> meta = new HashMap<>();

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder question(String question) {
            this.question = question;
            return this;
        }

        public Builder answer(String answer) {
            this.answer = answer;
            return this;
        }

        public Builder image(String image) {
            this.image = image;
            return this;
        }

        public Builder link(String link) {
            this.link = link;
            return this;
        }

        public Builder questionVector(float[] questionVector) {
            this.questionVector = questionVector;
            return this;
        }

        public Builder meta(Map<String, Object> meta) {
            this.meta = meta;
            return this;
        }

        public FaqDocument build() {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(question, "question must not be null");
            Objects.requireNonNull(answer, "answer must not be null");
            Objects.requireNonNull(questionVector, "questionVector must not be null");
            return new FaqDocument(this);
        }
    }

    // Immutable getters
    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getCategory() {
        return category;
    }

    public String getQuestion() {
        return question;
    }

    public String getAnswer() {
        return answer;
    }

    public String getImage() {
        return image;
    }

    public String getLink() {
        return link;
    }

    public float[] getQuestionVector() {
        return questionVector;
    }

    public Map<String, Object> getMeta() {
        return meta;
    }
}