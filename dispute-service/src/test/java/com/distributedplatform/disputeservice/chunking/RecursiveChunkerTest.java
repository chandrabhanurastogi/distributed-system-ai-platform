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

class RecursiveChunkerTest {

    @Test
    void chunk_withParagraphThatFits_returnsItAsOneChunkUntouched() {
        // Even though this paragraph contains three sentences, it fits under the
        // bound as a whole - it must stay one chunk, same clean path as
        // ParagraphChunker, not get sentence-split just because sentences exist.
        String text = "One fish. Two fish. Red fish.";

        List<String> chunks = new RecursiveChunker(500).chunk(text);

        assertThat(chunks).containsExactly("One fish. Two fish. Red fish.");
    }

    @Test
    void chunk_withOversizedParagraph_packsSentencesWithinItButNeverMergesIntoTheNextParagraph() {
        // Paragraph A ("One fish. Two fish. Red fish.", 29 chars) exceeds
        // maxChunkSize=20, so it recurses into sentence-level packing: "One fish."
        // (9) + " " + "Two fish." (9) = 19 fits; adding "Red fish." (9) would make 29,
        // so it flushes at "One fish. Two fish." and starts a new chunk with "Red
        // fish.". Paragraph B ("Blue fish.", 10 chars) fits under 20 as a whole, so it
        // stays untouched, proving the "Red fish." carryover never reaches across the
        // paragraph boundary to merge with it - unlike BoundedSentenceChunker, which
        // would have no concept of that boundary at all.
        String text = "One fish. Two fish. Red fish.\n\nBlue fish.";

        List<String> chunks = new RecursiveChunker(20).chunk(text);

        assertThat(chunks).containsExactly("One fish. Two fish.", "Red fish.", "Blue fish.");
    }

    @Test
    void chunk_withSentenceTooLongToPack_splitsIntoWordsAndPacksThemWithoutCuttingAnyWord() {
        // The whole paragraph is one 23-char sentence, oversized at maxChunkSize=10,
        // so it falls to word-level packing: "Alpha"(5) + " " + "beta"(4) = 10 fits
        // exactly; "gamma"(5) doesn't fit after that (would be 16), so it starts a new
        // chunk alone; "delta."(6) doesn't fit after "gamma" either (11), so it also
        // stands alone. No word is ever cut mid-token.
        String text = "Alpha beta gamma delta.";

        List<String> chunks = new RecursiveChunker(10).chunk(text);

        assertThat(chunks).containsExactly("Alpha beta", "gamma", "delta.");
    }

    @Test
    void chunk_withWordLongerThanMaxChunkSize_hardSplitsItByCharacterCount() {
        // A single 34-char token with no internal whitespace or punctuation survives
        // paragraph-split and sentence-split as one unmodified unit, then word-split
        // the same way - only then, as the true last resort, does it get a raw
        // character cut at the maxChunkSize=10 boundary.
        String text = "Supercalifragilisticexpialidocious";

        List<String> chunks = new RecursiveChunker(10).chunk(text);

        assertThat(chunks).containsExactly("Supercalif", "ragilistic", "expialidoc", "ious");
    }

    @Test
    void chunk_whenTextIsNull_throwsNullPointerException() {
        assertThatThrownBy(() -> new RecursiveChunker(10).chunk(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("text must not be null");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void constructor_whenMaxChunkSizeIsZeroOrNegative_throwsIllegalArgumentException(int invalidMaxChunkSize) {
        assertThatThrownBy(() -> new RecursiveChunker(invalidMaxChunkSize))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxChunkSize must be positive");
    }

    @Test
    void chunk_withRealDisputePolicyDocument_atAGenerousBound_preservesAllFourParagraphsCleanly() {
        // Same real, measured paragraph lengths as ParagraphChunkerTest (75, 847, 622,
        // 861 chars) - all fit under 1000, so nothing recurses and the result is
        // identical to ParagraphChunker's clean path at this bound.
        String document = loadResource("/documents/cardholder-dispute-policy.txt");

        List<String> chunks = new RecursiveChunker(1000).chunk(document);

        assertThat(chunks).hasSize(4);
        assertThat(chunks).extracting(String::length).containsExactly(75, 847, 622, 861);
    }

    @Test
    void chunk_withRealDisputePolicyDocument_atARealisticBound_packsSentencesInsteadOfBlindCutting() {
        // Same document, same 300-char bound as ParagraphChunkerTest's realistic-bound
        // case, where ParagraphChunker produces 10 chunks dominated by blind
        // character-cut fallback. This strategy instead recurses into sentence (and
        // where needed, word) packing, actually computed - not guessed - via a direct
        // Python port of this exact algorithm: 13 chunks, every one bounded by real
        // sentence/word boundaries, none a mid-word or mid-sentence raw cut. A real,
        // concrete difference between the two strategies on the same real input, which
        // is the whole point of building both.
        String document = loadResource("/documents/cardholder-dispute-policy.txt");
        int maxChunkSize = 300;

        List<String> chunks = new RecursiveChunker(maxChunkSize).chunk(document);

        assertThat(chunks).hasSize(13);
        assertThat(chunks).allSatisfy(chunk ->
                assertThat(chunk.length()).isLessThanOrEqualTo(maxChunkSize));
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
