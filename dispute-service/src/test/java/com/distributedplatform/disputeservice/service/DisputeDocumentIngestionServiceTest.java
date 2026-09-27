package com.distributedplatform.disputeservice.service;

import com.distributedplatform.disputeservice.DisputeDocument;
import com.distributedplatform.disputeservice.dao.DisputeDocumentRepository;
import com.distributedplatform.disputeservice.embedding.OllamaEmbeddingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DisputeDocumentIngestionServiceTest {

    private static final String EMBEDDING_MODEL = "nomic-embed-text";

    @Mock
    private OllamaEmbeddingService embeddingService;

    @Mock
    private DisputeDocumentRepository repository;

    @Test
    void ingest_chunksEmbedsAndSavesEachChunkInOrder_returningGeneratedIds() {
        // sentenceA is exactly 1000 chars - the ingestion service's max chunk size -
        // so it can't be grouped with anything else, deterministically producing
        // exactly two chunks: [sentenceA, sentenceB]. Chunking logic itself is already
        // proven by BoundedSentenceChunkerTest; this test is only about the
        // orchestration (embed each chunk, save each one, in order).
        String sentenceA = "A".repeat(999) + ".";
        String sentenceB = "Done.";
        String document = sentenceA + " " + sentenceB;

        double[] vectorA = {1.0, 0.0};
        double[] vectorB = {0.0, 1.0};
        when(embeddingService.embed(EMBEDDING_MODEL, sentenceA)).thenReturn(vectorA);
        when(embeddingService.embed(EMBEDDING_MODEL, sentenceB)).thenReturn(vectorB);
        // any(), not eq(new DisputeDocument(...)): DisputeDocument's record-generated
        // equals() compares the embedding array by reference, not contents, so matching
        // on a reconstructed DisputeDocument would silently break the moment ingest()
        // ever copies the array before saving. Returning by call order sidesteps that.
        when(repository.save(any(DisputeDocument.class))).thenReturn(101L, 102L);

        DisputeDocumentIngestionService ingestionService =
                new DisputeDocumentIngestionService(embeddingService, repository);

        List<Long> generatedIds = ingestionService.ingest(document);

        assertThat(generatedIds).containsExactly(101L, 102L);
        verify(embeddingService).embed(EMBEDDING_MODEL, sentenceA);
        verify(embeddingService).embed(EMBEDDING_MODEL, sentenceB);

        // Capture the real arguments save() was called with and assert on their actual
        // contents directly (text + array values), instead of trusting
        // DisputeDocument.equals() to do a deep comparison it doesn't actually do.
        ArgumentCaptor<DisputeDocument> savedDocuments = ArgumentCaptor.forClass(DisputeDocument.class);
        verify(repository, times(2)).save(savedDocuments.capture());

        DisputeDocument savedA = savedDocuments.getAllValues().get(0);
        DisputeDocument savedB = savedDocuments.getAllValues().get(1);

        assertThat(savedA.text()).isEqualTo(sentenceA);
        assertThat(savedA.embedding()).containsExactly(vectorA);
        assertThat(savedB.text()).isEqualTo(sentenceB);
        assertThat(savedB.embedding()).containsExactly(vectorB);
    }
}
