package com.distributedplatform.disputeservice.chunking;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One paragraph is always one chunk - never packed with a neighboring paragraph even
 * when both would easily fit under maxChunkSize together. A paragraph is treated as a
 * coherent retrieval unit; merging two unrelated paragraphs just because they're both
 * short is a precision risk, not a free optimization. maxChunkSize is therefore only a
 * fallback trigger, not a packing bound: a paragraph exceeding it is hard-split by
 * character count - a single-level fallback, same as BoundedSentenceChunker's, not the
 * general recursive strategy Phase 8 compares later.
 */
@Component("paragraph")
public class ParagraphChunker implements ChunkingStrategy {

    private static final Pattern PARAGRAPH_BOUNDARY = Pattern.compile("\\n\\s*\\n");

    private final int maxChunkSize;

    public ParagraphChunker(@Value("${dispute.chunking.paragraph.max-chunk-size}") int maxChunkSize) {
        if (maxChunkSize <= 0) {
            throw new IllegalArgumentException("maxChunkSize must be positive");
        }
        this.maxChunkSize = maxChunkSize;
    }

    @Override
    public List<String> chunk(String text) {
        Objects.requireNonNull(text, "text must not be null");

        List<String> chunks = new ArrayList<>();
        for (String paragraph : splitIntoParagraphs(text)) {
            if (paragraph.length() > maxChunkSize) {
                chunks.addAll(hardSplitByCharacterCount(paragraph));
            } else {
                chunks.add(paragraph);
            }
        }
        return chunks;
    }

    private List<String> splitIntoParagraphs(String text) {
        List<String> paragraphs = new ArrayList<>();
        for (String candidate : PARAGRAPH_BOUNDARY.split(text.trim())) {
            String trimmed = candidate.trim();
            if (!trimmed.isEmpty()) {
                paragraphs.add(trimmed);
            }
        }
        return paragraphs;
    }

    private List<String> hardSplitByCharacterCount(String paragraph) {
        List<String> pieces = new ArrayList<>();
        int start = 0;
        while (start < paragraph.length()) {
            int end = Math.min(start + maxChunkSize, paragraph.length());
            pieces.add(paragraph.substring(start, end));
            start = end;
        }
        return pieces;
    }
}
