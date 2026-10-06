package com.distributedplatform.disputeservice.chunking;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The naive baseline every other strategy is compared against: cuts text every
 * chunkSize characters with zero awareness of sentence, word, or paragraph
 * boundaries - a cut can and will land mid-word.
 */
@Component("fixedSize")
public class FixedSizeChunker implements ChunkingStrategy {

    private final int chunkSize;

    public FixedSizeChunker(@Value("${dispute.chunking.fixed-size.chunk-size}") int chunkSize) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be positive");
        }
        this.chunkSize = chunkSize;
    }

    @Override
    public List<String> chunk(String text) {
        Objects.requireNonNull(text, "text must not be null");

        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            chunks.add(text.substring(start, end));
            start = end;
        }
        return chunks;
    }
}
