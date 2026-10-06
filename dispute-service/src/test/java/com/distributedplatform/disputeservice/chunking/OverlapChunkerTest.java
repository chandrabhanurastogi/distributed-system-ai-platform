package com.distributedplatform.disputeservice.chunking;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OverlapChunkerTest {

    @Test
    void chunk_withOverlap_eachChunkRepeatsTheTailOfThePreviousOne() {
        // 20 letters, chunkSize=10, overlapSize=3 -> stride=7.
        // [0,10): "ABCDEFGHIJ"; next starts at 7, [7,17): "HIJKLMNOPQ" - its first 3
        // chars ("HIJ") are exactly the first chunk's last 3 chars, proving the
        // overlap is real content repetition, not a coincidence of length.
        // [14,20): "OPQRST" reaches the end, so the window stops.
        String text = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".substring(0, 20);

        List<String> chunks = new OverlapChunker(10, 3).chunk(text);

        assertThat(chunks).containsExactly("ABCDEFGHIJ", "HIJKLMNOPQ", "OPQRST");
        assertThat(chunks.get(0)).endsWith("HIJ");
        assertThat(chunks.get(1)).startsWith("HIJ");
    }

    @Test
    void chunk_withZeroOverlap_behavesExactlyLikeFixedSizeChunker() {
        // Same fixture and chunkSize as FixedSizeChunkerTest's mid-word-cut case -
        // overlapSize=0 means stride == chunkSize, which is just FixedSizeChunker's
        // behavior by another name. Proves the degenerate case collapses correctly
        // rather than off-by-one-ing into something subtly different.
        String text = "Hello world";

        List<String> chunks = new OverlapChunker(5, 0).chunk(text);

        assertThat(chunks).containsExactly("Hello", " worl", "d");
    }

    @Test
    void chunk_withTextShorterThanChunkSize_returnsItAsASingleChunk() {
        String text = "Hi";

        List<String> chunks = new OverlapChunker(500, 50).chunk(text);

        assertThat(chunks).containsExactly("Hi");
    }

    @Test
    void chunk_withTextExactlyEqualToChunkSize_returnsItAsASingleChunk() {
        String text = "Testing.";

        List<String> chunks = new OverlapChunker(8, 3).chunk(text);

        assertThat(chunks).containsExactly("Testing.");
    }

    @Test
    void chunk_withEmptyText_returnsEmptyList() {
        List<String> chunks = new OverlapChunker(10, 3).chunk("");

        assertThat(chunks).isEmpty();
    }

    @Test
    void chunk_whenTextIsNull_throwsNullPointerException() {
        assertThatThrownBy(() -> new OverlapChunker(10, 3).chunk(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("text must not be null");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void constructor_whenChunkSizeIsZeroOrNegative_throwsIllegalArgumentException(int invalidChunkSize) {
        assertThatThrownBy(() -> new OverlapChunker(invalidChunkSize, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("chunkSize must be positive");
    }

    @Test
    void constructor_whenOverlapSizeIsNegative_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new OverlapChunker(10, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("overlapSize must not be negative");
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 15})
    void constructor_whenOverlapSizeIsGreaterThanOrEqualToChunkSize_throwsIllegalArgumentException(int invalidOverlapSize) {
        assertThatThrownBy(() -> new OverlapChunker(10, invalidOverlapSize))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("overlapSize must be less than chunkSize");
    }

    @Test
    void chunk_withRealDisputePolicyDocument_neverExceedsChunkSizeAndOverlapsAcrossWindows() {
        // Real, measured result for this document at chunkSize=300/overlapSize=50
        // (stride=250): 10 windows - nine full 300-char chunks and one 162-char
        // remainder - every one capped at exactly chunkSize, computed via a direct
        // Python port of this algorithm, not guessed.
        String document = loadResource("/documents/cardholder-dispute-policy.txt");
        int chunkSize = 300;

        List<String> chunks = new OverlapChunker(chunkSize, 50).chunk(document);

        assertThat(chunks).hasSize(10);
        assertThat(chunks).allSatisfy(chunk ->
                assertThat(chunk.length()).isLessThanOrEqualTo(chunkSize));
        // The real overlap, proven on real text: the last 50 chars of the first
        // window must equal the first 50 chars of the second.
        String firstChunk = chunks.get(0);
        String secondChunk = chunks.get(1);
        assertThat(firstChunk.substring(firstChunk.length() - 50))
                .isEqualTo(secondChunk.substring(0, 50));
    }

    private String loadResource(String classpathLocation) {
        try (InputStream in = getClass().getResourceAsStream(classpathLocation)) {
            if (in == null) {
                throw new IllegalStateException("Resource not found on classpath: " + classpathLocation);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
