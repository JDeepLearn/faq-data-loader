package io.github.jdeeplearn.rag.service;

import io.github.jdeeplearn.rag.index.VectorSearchIndexManager;
import io.github.jdeeplearn.rag.model.FaqDocument;
import io.github.jdeeplearn.rag.repository.CouchbaseFaqRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Verifies that FaqUploaderService:
 *  - Calls EmbeddingClient
 *  - Creates FaqDocument correctly
 *  - Persists via CouchbaseFaqRepository
 *  - Ensures vector index via VectorSearchIndexManager
 */
@ExtendWith(MockitoExtension.class)
class FaqUploaderServiceTest {

    private EmbeddingClient embeddingClient;
    private CouchbaseFaqRepository repository;
    private VectorSearchIndexManager indexManager;
    private FaqUploaderService service;

    @BeforeEach
    void setUp() {
        embeddingClient = mock(EmbeddingClient.class);
        repository = mock(CouchbaseFaqRepository.class);
        indexManager = mock(VectorSearchIndexManager.class);

        // Default stubs
        when(embeddingClient.getProvider()).thenReturn("intfloat");
        when(embeddingClient.getModelName()).thenReturn("e5-large-v2");
        when(embeddingClient.embed(anyString())).thenReturn(fakeVector(1024));

        service = new FaqUploaderService(
                embeddingClient,
                repository,
                indexManager,
                1024,
                2
        );
    }

    @Test
    void shouldUploadFaqsAndEnsureIndex() {
        List<FaqUploaderService.FaqInput> faqs = List.of(
                new FaqUploaderService.FaqInput(
                        "Account",
                        "How do I reset my password?",
                        "Go to settings.",
                        null,
                        null
                ),
                new FaqUploaderService.FaqInput(
                        "Profile",
                        "How do I update my email?",
                        "Edit your profile.",
                        null,
                        null
                )
        );

        service.uploadFaqs(faqs);

        // Index should be ensured once
        verify(indexManager, times(1)).ensureIndex();

        // Embedding called once per FAQ
        verify(embeddingClient, times(2)).embed(anyString());

        // Repository insert called once per FAQ
        ArgumentCaptor<FaqDocument> captor = ArgumentCaptor.forClass(FaqDocument.class);
        verify(repository, times(2)).insertFaq(captor.capture());

        List<FaqDocument> docs = captor.getAllValues();
        assertThat(docs).hasSize(2);

        // Assert both categories exist, order-independent
        List<String> categories = docs.stream().map(FaqDocument::getCategory).toList();
        assertThat(categories).containsExactlyInAnyOrder("Account", "Profile");

        // Assert that every vector has the expected length
        docs.forEach(doc -> assertThat(doc.getQuestionVector()).hasSize(1024));

        // Assert that at least one question mentions "password"
        assertThat(docs.stream().anyMatch(d -> d.getQuestion().contains("password"))).isTrue();
    }

    private static float[] fakeVector(int dim) {
        float[] v = new float[dim];
        for (int i = 0; i < dim; i++) {
            v[i] = 0.001f * i;
        }
        return v;
    }
}
