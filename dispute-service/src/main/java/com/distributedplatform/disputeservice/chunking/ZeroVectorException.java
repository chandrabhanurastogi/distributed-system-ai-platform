package com.distributedplatform.disputeservice.chunking;

/**
 * Mirrors ADR-0008's already-decided answer for this exact undefined operation (cosine
 * similarity where either vector has zero magnitude) - thrown rather than silently
 * returning NaN, so a degenerate sentence embedding can't quietly corrupt
 * SemanticChunker's percentile-threshold computation with no exception and no log line.
 * Kept local rather than depending on llm-fundamentals's equivalent, for the same
 * reason SemanticChunker's cosineSimilarity formula itself is duplicated locally
 * (ADR-0011's duplication test: a small, simple, no-known-bugs concept, cheap to
 * duplicate versus taking a dependency on a separate runnable application).
 */
public class ZeroVectorException extends RuntimeException {

    public ZeroVectorException(String message) {
        super(message);
    }
}
