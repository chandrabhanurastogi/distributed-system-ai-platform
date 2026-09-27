package com.distributedplatform.disputeservice.service;

import com.distributedplatform.disputeservice.DisputeDocument;
import com.distributedplatform.disputeservice.chunking.BoundedSentenceChunker;
import com.distributedplatform.disputeservice.dao.DisputeDocumentRepository;
import com.distributedplatform.disputeservice.embedding.OllamaEmbeddingService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DisputeDocumentIngestionService {

    private static final String EMBEDDING_MODEL = "nomic-embed-text";
    private static final int MAX_CHUNK_SIZE = 1000;

    private final BoundedSentenceChunker chunker = new BoundedSentenceChunker();
    private final OllamaEmbeddingService embeddingService;
    private final DisputeDocumentRepository repository;

    public DisputeDocumentIngestionService(OllamaEmbeddingService embeddingService,
                                            DisputeDocumentRepository repository) {
        this.embeddingService = embeddingService;
        this.repository = repository;
    }

    public List<Long> ingest(String documentText) {
        List<Long> generatedIds = new ArrayList<>();

        for (String chunkText : chunker.chunk(documentText, MAX_CHUNK_SIZE)) {
            double[] embedding = embeddingService.embed(EMBEDDING_MODEL, chunkText);
            generatedIds.add(repository.save(new DisputeDocument(chunkText, embedding)));
        }

        return generatedIds;
    }
}
