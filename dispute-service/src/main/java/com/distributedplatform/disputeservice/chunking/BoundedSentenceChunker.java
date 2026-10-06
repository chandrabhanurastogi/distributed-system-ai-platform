package com.distributedplatform.disputeservice.chunking;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Groups sentences into chunks up to maxChunkSize characters. Bounded size is the hard
 * contract; sentence-boundary preservation is secondary and yields when the two
 * conflict - a single sentence longer than maxChunkSize is hard-split at the character
 * limit as an explicit fallback, never left unbounded. This is a single-level fallback
 * (sentence-grouping, then one raw character-count cut), not the general recursive
 * paragraph/sentence/word/character strategy Phase 8 compares later.
 */
@Component("boundedSentenceChunker")
public class BoundedSentenceChunker implements ChunkingStrategy {

    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("(?<=[.!?])\\s+");

    private final int maxChunkSize;

    public BoundedSentenceChunker(@Value("${dispute.chunking.bounded-sentence.max-chunk-size}") int maxChunkSize) {
        if (maxChunkSize <= 0) {
            throw new IllegalArgumentException("maxChunkSize must be positive");
        }
        this.maxChunkSize = maxChunkSize;
    }

    @Override
    public List<String> chunk(String text) {
        Objects.requireNonNull(text, "text must not be null");

        List<String> chunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();

        for (String sentence : splitIntoSentences(text)) {
            if (sentence.length() > maxChunkSize) {
                if (!currentChunk.isEmpty()) {
                    chunks.add(currentChunk.toString());
                    currentChunk = new StringBuilder();
                }
                chunks.addAll(hardSplitByCharacterCount(sentence));
                continue;
            }

            int prospectiveLength = currentChunk.isEmpty()
                    ? sentence.length()
                    : currentChunk.length() + 1 + sentence.length();

            if (prospectiveLength > maxChunkSize) {
                chunks.add(currentChunk.toString());
                currentChunk = new StringBuilder(sentence);
            } else {
                if (!currentChunk.isEmpty()) {
                    currentChunk.append(' ');
                }
                currentChunk.append(sentence);
            }
        }

        if (!currentChunk.isEmpty()) {
            chunks.add(currentChunk.toString());
        }

        return chunks;
    }

    private List<String> splitIntoSentences(String text) {
        List<String> sentences = new ArrayList<>();
        for (String candidate : SENTENCE_BOUNDARY.split(text.trim())) {
            String trimmed = candidate.trim();
            if (!trimmed.isEmpty()) {
                sentences.add(trimmed);
            }
        }
        return sentences;
    }

    private List<String> hardSplitByCharacterCount(String sentence) {
        List<String> pieces = new ArrayList<>();
        int start = 0;
        while (start < sentence.length()) {
            int end = Math.min(start + maxChunkSize, sentence.length());
            pieces.add(sentence.substring(start, end));
            start = end;
        }
        return pieces;
    }
}