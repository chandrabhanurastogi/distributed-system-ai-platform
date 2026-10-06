package com.distributedplatform.disputeservice.chunking;

import com.distributedplatform.disputeservice.embedding.OllamaEmbeddingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Chunk boundaries come from meaning, not structure: consecutive sentences are
 * embedded, the cosine distance between each pair is computed, and a "breakpoint" is
 * any gap whose distance exceeds the Nth percentile of this document's own distance
 * distribution - self-calibrating per document, not a universal magic number.
 * Paragraph breaks are deliberately ignored; the whole document is one flat sentence
 * sequence, since embedding similarity alone is meant to be the only signal here - the
 * one strategy among the six whose boundaries don't come from any structural rule.
 *
 * A group of sentences between breakpoints that still exceeds maxChunkSize is
 * re-packed at the sentence level (never merging into the next group - that boundary
 * is the one thing this algorithm actually computed), falling back further to word,
 * then raw-character packing, same cascade shape as RecursiveChunker. Cosine
 * similarity is a small local method, not a dependency on llm-fundamentals' VectorMath
 * - per ADR-0011's duplication test, a ~10-line formula with no known bugs is cheap to
 * duplicate locally rather than taking a dependency on a separate runnable
 * application.
 *
 * With fewer than 2 sentences there's nothing to compare, so embedding is skipped
 * entirely - not an optimization worth hiding, just the correct base case.
 */
@Component("semantic")
public class SemanticChunker implements ChunkingStrategy {

    private static final String EMBEDDING_MODEL = "nomic-embed-text";
    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("(?<=[.!?])\\s+");
    private static final Pattern WORD_BOUNDARY = Pattern.compile("\\s+");

    private final OllamaEmbeddingService embeddingService;
    private final int maxChunkSize;
    private final double breakpointPercentile;

    public SemanticChunker(OllamaEmbeddingService embeddingService,
                            @Value("${dispute.chunking.semantic.max-chunk-size}") int maxChunkSize,
                            @Value("${dispute.chunking.semantic.breakpoint-percentile}") double breakpointPercentile) {
        if (maxChunkSize <= 0) {
            throw new IllegalArgumentException("maxChunkSize must be positive");
        }
        if (breakpointPercentile < 0.0 || breakpointPercentile > 1.0) {
            throw new IllegalArgumentException("breakpointPercentile must be between 0.0 and 1.0");
        }
        this.embeddingService = embeddingService;
        this.maxChunkSize = maxChunkSize;
        this.breakpointPercentile = breakpointPercentile;
    }

    @Override
    public List<String> chunk(String text) {
        Objects.requireNonNull(text, "text must not be null");

        List<String> sentences = split(text, SENTENCE_BOUNDARY);
        if (sentences.isEmpty()) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();
        for (List<String> group : groupBySemanticBreakpoints(sentences)) {
            chunks.addAll(pack(group, this::splitOversizedSentence));
        }
        return chunks;
    }

    private List<List<String>> groupBySemanticBreakpoints(List<String> sentences) {
        List<List<String>> groups = new ArrayList<>();

        if (sentences.size() == 1) {
            List<String> onlyGroup = new ArrayList<>();
            onlyGroup.add(sentences.get(0));
            groups.add(onlyGroup);
            return groups;
        }

        double[][] embeddings = new double[sentences.size()][];
        for (int i = 0; i < sentences.size(); i++) {
            embeddings[i] = embeddingService.embed(EMBEDDING_MODEL, sentences.get(i));
        }

        double[] distances = new double[sentences.size() - 1];
        for (int i = 0; i < distances.length; i++) {
            distances[i] = 1.0 - cosineSimilarity(embeddings[i], embeddings[i + 1]);
        }

        double threshold = percentile(distances, breakpointPercentile);

        List<String> currentGroup = new ArrayList<>();
        currentGroup.add(sentences.get(0));
        for (int i = 0; i < distances.length; i++) {
            if (distances[i] > threshold) {
                groups.add(currentGroup);
                currentGroup = new ArrayList<>();
            }
            currentGroup.add(sentences.get(i + 1));
        }
        groups.add(currentGroup);

        return groups;
    }

    /**
     * Nearest-rank percentile: no interpolation between ranks. Simple and explainable,
     * at the cost of being one specific definition among several valid ones - with a
     * small sample, the rank can land on the maximum itself, making the threshold
     * unreachable by construction, not by any flaw in the real distances.
     */
    private double percentile(double[] values, double p) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        int rank = (int) Math.ceil(p * sorted.length);
        int index = Math.min(Math.max(rank, 1), sorted.length) - 1;
        return sorted[index];
    }

    private double cosineSimilarity(double[] a, double[] b) {
        double dot = 0.0;
        double magA = 0.0;
        double magB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            magA += a[i] * a[i];
            magB += b[i] * b[i];
        }
        if (magA == 0.0 || magB == 0.0) {
            throw new ZeroVectorException(
                    "Cosine similarity is undefined for a zero-magnitude sentence embedding");
        }
        return dot / (Math.sqrt(magA) * Math.sqrt(magB));
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
