package com.distributedplatform.disputeservice.service;

import com.distributedplatform.disputeservice.DisputeDocument;
import com.distributedplatform.disputeservice.SourceDocument;
import com.distributedplatform.disputeservice.chunking.BoundedSentenceChunker;
import com.distributedplatform.disputeservice.dao.DisputeDocumentRepository;
import com.distributedplatform.disputeservice.dao.SourceDocumentRepository;
import com.distributedplatform.disputeservice.embedding.OllamaEmbeddingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class DisputeDocumentIngestionService {

    private static final String EMBEDDING_MODEL = "nomic-embed-text";
    private static final int MAX_CHUNK_SIZE = 1000;

    private final BoundedSentenceChunker chunker;
    private final OllamaEmbeddingService embeddingService;
    private final DisputeDocumentRepository disputeDocumentRepository;
    private final SourceDocumentRepository sourceDocumentRepository;

    public DisputeDocumentIngestionService(BoundedSentenceChunker chunker,
                                            OllamaEmbeddingService embeddingService,
                                            DisputeDocumentRepository disputeDocumentRepository,
                                            SourceDocumentRepository sourceDocumentRepository) {
        this.chunker = chunker;
        this.embeddingService = embeddingService;
        this.disputeDocumentRepository = disputeDocumentRepository;
        this.sourceDocumentRepository = sourceDocumentRepository;
    }

    public List<Long> ingest(String documentText) {
        List<Long> generatedIds = new ArrayList<>();

        for (String chunkText : chunker.chunk(documentText, MAX_CHUNK_SIZE)) {
            double[] embedding = embeddingService.embed(EMBEDDING_MODEL, chunkText);
            generatedIds.add(disputeDocumentRepository.save(new DisputeDocument(chunkText, embedding)));
        }

        return generatedIds;
    }

    // ADR-0010: check-if-unchanged, upsert hash, delete stale chunks, insert new ones -
    // one atomic unit. The hash upsert MUST happen inside this same transaction, not
    // before it: if it were a separate, already-committed write, a failure partway
    // through the chunk delete/insert below would roll back the chunk changes but
    // leave the hash already pointing at the new content - source_documents would
    // then claim "this document is up to date" while dispute_documents still held the
    // OLD chunks, and every future run would see the hash already match and skip
    // retrying, forever. Keeping the hash upsert inside this transaction means either
    // everything commits together or nothing does, so the tracking hash can never lie
    // about what is actually stored.
    @Transactional
    public IngestionOutcome reingestFromSource(String sourceIdentifier, String documentText, String contentHash) {
        Optional<SourceDocument> existing = sourceDocumentRepository.findBySourceIdentifier(sourceIdentifier);

        if (existing.isPresent() && existing.get().contentHash().equals(contentHash)) {
            return IngestionOutcome.SKIPPED_UNCHANGED;
        }

        LocalDateTime now = LocalDateTime.now();
        Long sourceDocumentId;
        if (existing.isPresent()) {
            sourceDocumentId = existing.get().id();
            sourceDocumentRepository.updateHash(sourceDocumentId, contentHash, now);
        } else {
            sourceDocumentId = sourceDocumentRepository.insert(
                    new SourceDocument(null, sourceIdentifier, contentHash, now));
        }

        disputeDocumentRepository.deleteBySourceDocumentId(sourceDocumentId);

        for (String chunkText : chunker.chunk(documentText, MAX_CHUNK_SIZE)) {
            double[] embedding = embeddingService.embed(EMBEDDING_MODEL, chunkText);
            disputeDocumentRepository.save(new DisputeDocument(chunkText, embedding, sourceDocumentId));
        }

        return IngestionOutcome.INGESTED;
    }
}
