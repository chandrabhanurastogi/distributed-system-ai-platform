package com.distributedplatform.disputeservice.chunking;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A sliding window over raw characters, same blunt no-boundary-awareness philosophy as
 * FixedSizeChunker, but the next chunk starts overlapSize characters before the
 * previous one ended (stride = chunkSize - overlapSize) instead of exactly where it
 * ended. The tail of one chunk is deliberately repeated as the head of the next, so
 * content sitting on a chunk boundary still appears whole in at least one chunk.
 * Deliberately not boundary-aware: mixing in word/sentence awareness here would
 * confound "did overlap help" with "did boundary-awareness help" once this gets
 * compared against the other strategies - overlap is the one variable this strategy
 * exists to isolate.
 */
@Component("overlap")
public class OverlapChunker implements ChunkingStrategy {

    private final int chunkSize;
    private final int overlapSize;

    public OverlapChunker(@Value("${dispute.chunking.overlap.chunk-size}") int chunkSize,
                           @Value("${dispute.chunking.overlap.overlap-size}") int overlapSize) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be positive");
        }
        if (overlapSize < 0) {
            throw new IllegalArgumentException("overlapSize must not be negative");
        }
        if (overlapSize >= chunkSize) {
            throw new IllegalArgumentException("overlapSize must be less than chunkSize");
        }
        this.chunkSize = chunkSize;
        this.overlapSize = overlapSize;
    }

    @Override
    public List<String> chunk(String text) {
        Objects.requireNonNull(text, "text must not be null");

        List<String> chunks = new ArrayList<>();
        int stride = chunkSize - overlapSize;
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            chunks.add(text.substring(start, end));
            if (end == text.length()) {
                break;
            }
            start += stride;
        }
        return chunks;
    }
}
