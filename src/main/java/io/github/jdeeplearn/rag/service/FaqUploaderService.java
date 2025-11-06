package io.github.jdeeplearn.rag.service;

import io.github.jdeeplearn.rag.index.VectorSearchIndexManager;
import io.github.jdeeplearn.rag.model.FaqDocument;
import io.github.jdeeplearn.rag.repository.CouchbaseFaqRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Orchestrates FAQ ingestion:
 *  1. Embedding generation.
 *  2. Document persistence.
 *  3. Index initialization.
 */
@Service
public class FaqUploaderService {

    private static final Logger log = LogManager.getLogger(FaqUploaderService.class);

    private final EmbeddingClient embeddingClient;
    private final CouchbaseFaqRepository repository;
    private final VectorSearchIndexManager indexManager;
    private final ExecutorService executor;
    private final int embeddingDim;

    public FaqUploaderService(
            EmbeddingClient embeddingClient,
            CouchbaseFaqRepository repository,
            VectorSearchIndexManager indexManager,
            @Value("${embedding.dim:1024}") int embeddingDim,
            @Value("${uploader.threads:4}") int threads
    ) {
        this.embeddingClient = embeddingClient;
        this.repository = repository;
        this.indexManager = indexManager;
        this.embeddingDim = embeddingDim;
        this.executor = Executors.newFixedThreadPool(threads);
    }

    public record FaqInput(
            String category,
            String question,
            String answer,
            String image,
            String link
    ) {}

    /**
     * Upload multiple FAQs into Couchbase.
     */
    public void uploadFaqs(List<FaqInput> faqs) {
        log.info("Starting upload of {} FAQ entries", faqs.size());

        // Ensure vector index exists first
        indexManager.ensureIndex();

        faqs.forEach(faq ->
                executor.submit(() -> processFaq(faq))
        );

        executor.shutdown();
        while (!executor.isTerminated()) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        log.info("FAQ upload completed successfully.");
    }

    private void processFaq(FaqInput input) {
        try {
            String id = "faq-" + UUID.randomUUID();
            float[] vector = embeddingClient.embed(input.question());

            FaqDocument doc = FaqDocument.of(
                    id,
                    input.category(),
                    input.question(),
                    input.answer(),
                    input.image(),
                    input.link(),
                    vector,
                    embeddingClient.getProvider(),
                    embeddingClient.getModelName(),
                    embeddingDim,
                    "cosine",
                    "faq-loader",
                    "v1.0.0"
            );

            repository.insertFaq(doc);
        } catch (Exception e) {
            log.error("Error processing FAQ '{}': {}", input.question(), e.getMessage(), e);
        }
    }
}
