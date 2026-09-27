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

class BoundedSentenceChunkerTest {

    private final BoundedSentenceChunker chunker = new BoundedSentenceChunker();

    @Test
    void chunk_withMultipleSentences_groupsThemUntilTheSizeLimitThenStartsANewChunk() {
        // "One fish." "Two fish." "Red fish." are 9 chars each; joined with single
        // spaces: "One fish. Two fish. Red fish." is exactly 29 chars - fits under 30.
        // Adding "Blue fish." (10 chars) would push it to 40, so it starts a new chunk.
        String text = "One fish. Two fish. Red fish. Blue fish.";

        List<String> chunks = chunker.chunk(text, 30);

        assertThat(chunks).containsExactly(
                "One fish. Two fish. Red fish.",
                "Blue fish.");
    }

    @Test
    void chunk_withInputShorterThanOneChunk_returnsItAsASingleChunk() {
        String text = "Distributed systems require reliable coordination.";

        List<String> chunks = chunker.chunk(text, 500);

        assertThat(chunks).containsExactly("Distributed systems require reliable coordination.");
    }

    @Test
    void chunk_whenGroupedSentencesLandExactlyOnTheSizeLimit_stillFitsInOneChunk() {
        // "Hi." (3 chars) + " " (1) + "Bye." (4 chars) = 8 chars exactly, matching
        // maxChunkSize - proves the boundary is inclusive (<=), not off-by-one.
        String text = "Hi. Bye.";

        List<String> chunks = chunker.chunk(text, 8);

        assertThat(chunks).containsExactly("Hi. Bye.");
    }

    @Test
    void chunk_whenASingleSentenceExceedsMaxChunkSize_hardSplitsItByCharacterCount() {
        // "1234567890123456." is 17 chars - longer than maxChunkSize=10, so it can't be
        // grouped like a normal sentence. Fallback hard-splits it at the character
        // limit: "1234567890" (10 chars) then "123456." (7 chars) - neither piece is a
        // clean sentence, which is the explicit, accepted trade-off of this fallback.
        // The preceding normal sentence ("Hi.") must be flushed as its own chunk first,
        // not merged with the hard-split fragments.
        String text = "Hi. 1234567890123456.";

        List<String> chunks = chunker.chunk(text, 10);

        assertThat(chunks).containsExactly("Hi.", "1234567890", "123456.");
    }

    @Test
    void chunk_whenASingleSentenceExactlyEqualsMaxChunkSize_isNotTreatedAsOversized() {
        // "Testing." is exactly 8 chars, matching maxChunkSize exactly - proves the
        // fallback trigger (sentence.length() > maxChunkSize) is strict, so a sentence
        // sitting exactly on the limit takes the normal path, not the hard-split one.
        String text = "Testing.";

        List<String> chunks = chunker.chunk(text, 8);

        assertThat(chunks).containsExactly("Testing.");
    }

    @Test
    void chunk_whenTextIsNull_throwsNullPointerException() {
        assertThatThrownBy(() -> chunker.chunk(null, 10))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("text must not be null");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void chunk_whenMaxChunkSizeIsZeroOrNegative_throwsIllegalArgumentException(int invalidMaxChunkSize) {
        assertThatThrownBy(() -> chunker.chunk("Some text.", invalidMaxChunkSize))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxChunkSize must be positive");
    }

    @Test
    void chunk_withRealDisputePolicyDocument_neverExceedsMaxChunkSizeAndProducesMultipleChunks() {
        // Real document (not hand-crafted), including one deliberately long,
        // clause-heavy sentence expected to exercise the hard-split fallback path.
        // Exact chunk boundaries aren't hand-verified here - that's already proven by
        // the small, hand-traceable cases above. What's provable and asserted instead
        // is the one invariant this whole design exists to guarantee: no chunk, from
        // either the normal or the fallback path, is ever allowed to exceed
        // maxChunkSize.
        String document = loadResource("/documents/cardholder-dispute-policy.txt");
        int maxChunkSize = 300;

        List<String> chunks = chunker.chunk(document, maxChunkSize);

        assertThat(chunks).isNotEmpty();
        assertThat(chunks.size()).isGreaterThan(1);
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
