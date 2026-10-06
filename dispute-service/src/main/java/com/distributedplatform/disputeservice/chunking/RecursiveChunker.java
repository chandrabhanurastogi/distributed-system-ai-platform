package com.distributedplatform.disputeservice.chunking;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Cascades through progressively finer boundaries - paragraph, then sentence, then
 * word, then raw character - recursing into the next one only for a unit that's still
 * too big, rather than jumping straight to a character cut like the other strategies
 * do. A paragraph that fits stays one chunk, untouched (never merged with a sibling
 * paragraph, same rule as ParagraphChunker). An oversized paragraph is split into
 * sentences and greedily packed up to maxChunkSize, scoped to that one paragraph only -
 * unlike BoundedSentenceChunker, which packs sentences with no regard for paragraph
 * boundaries. A sentence still too big on its own is split into words and packed the
 * same way. A single word longer than maxChunkSize (essentially never happens in real
 * prose) is the true last resort: a raw character cut.
 *
 * Deliberately reimplements its own paragraph/sentence/word splitting rather than
 * delegating to ParagraphChunker/BoundedSentenceChunker: both of those already apply
 * their own hard-split fallback inside chunk() itself, so calling them as black boxes
 * would fire that fallback before this class ever got a chance to recurse into the next
 * finer boundary. Per ADR-0011's own duplication test (complexity/risk of what's
 * duplicated, not instance count), a one-line regex with no known bugs is cheap enough
 * to duplicate locally rather than extracting shared surface area these two
 * already-finished classes don't otherwise need.
 */
@Component("recursive")
public class RecursiveChunker implements ChunkingStrategy {

    private static final Pattern PARAGRAPH_BOUNDARY = Pattern.compile("\\n\\s*\\n");
    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("(?<=[.!?])\\s+");
    private static final Pattern WORD_BOUNDARY = Pattern.compile("\\s+");

    private final int maxChunkSize;

    public RecursiveChunker(@Value("${dispute.chunking.recursive.max-chunk-size}") int maxChunkSize) {
        if (maxChunkSize <= 0) {
            throw new IllegalArgumentException("maxChunkSize must be positive");
        }
        this.maxChunkSize = maxChunkSize;
    }

    @Override
    public List<String> chunk(String text) {
        Objects.requireNonNull(text, "text must not be null");

        List<String> chunks = new ArrayList<>();
        for (String paragraph : split(text, PARAGRAPH_BOUNDARY)) {
            if (paragraph.length() <= maxChunkSize) {
                chunks.add(paragraph);
            } else {
                chunks.addAll(pack(split(paragraph, SENTENCE_BOUNDARY), this::splitOversizedSentence));
            }
        }
        return chunks;
    }

    private List<String> splitOversizedSentence(String sentence) {
        return pack(split(sentence, WORD_BOUNDARY), this::hardSplitByCharacterCount);
    }

    /**
     * Greedily packs units into chunks up to maxChunkSize. A single unit already over
     * the limit is flushed through the given fallback (the next finer boundary level)
     * instead of being forced into a chunk of its own.
     */
    private List<String> pack(List<String> units, Function<String, List<String>> fallback) {
        List<String> chunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();

        for (String unit : units) {
            if (unit.length() > maxChunkSize) {
                if (!currentChunk.isEmpty()) {
                    chunks.add(currentChunk.toString());
                    currentChunk = new StringBuilder();
                }
                chunks.addAll(fallback.apply(unit));
                continue;
            }

            int prospectiveLength = currentChunk.isEmpty()
                    ? unit.length()
                    : currentChunk.length() + 1 + unit.length();

            if (prospectiveLength > maxChunkSize) {
                chunks.add(currentChunk.toString());
                currentChunk = new StringBuilder(unit);
            } else {
                if (!currentChunk.isEmpty()) {
                    currentChunk.append(' ');
                }
                currentChunk.append(unit);
            }
        }

        if (!currentChunk.isEmpty()) {
            chunks.add(currentChunk.toString());
        }

        return chunks;
    }

    private List<String> split(String text, Pattern boundary) {
        List<String> units = new ArrayList<>();
        for (String candidate : boundary.split(text.trim())) {
            String trimmed = candidate.trim();
            if (!trimmed.isEmpty()) {
                units.add(trimmed);
            }
        }
        return units;
    }

    private List<String> hardSplitByCharacterCount(String word) {
        List<String> pieces = new ArrayList<>();
        int start = 0;
        while (start < word.length()) {
            int end = Math.min(start + maxChunkSize, word.length());
            pieces.add(word.substring(start, end));
            start = end;
        }
        return pieces;
    }
}
