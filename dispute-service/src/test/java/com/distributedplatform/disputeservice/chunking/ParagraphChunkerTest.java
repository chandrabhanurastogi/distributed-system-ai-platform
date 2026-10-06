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

class ParagraphChunkerTest {

    @Test
    void chunk_withMultipleSmallParagraphs_neverPacksThemTogetherEvenWhenTheyWouldFit() {
        // "Para one." (9) and "Para two." (9) would easily fit in one 500-char chunk
        // together, the way BoundedSentenceChunker packs sentences - but paragraph
        // chunking's whole point is that a paragraph is its own retrieval unit, so
        // this must stay two chunks regardless of how much headroom maxChunkSize has.
        String text = "Para one.\n\nPara two.";

        List<String> chunks = new ParagraphChunker(500).chunk(text);

        assertThat(chunks).containsExactly("Para one.", "Para two.");
    }

    @Test
    void chunk_whenAParagraphExceedsMaxChunkSize_hardSplitsItByCharacterCount() {
        // "1234567890123456" is 16 chars - longer than maxChunkSize=10 - so it can't
        // stand as one chunk. Fallback hard-splits it at the character limit:
        // "1234567890" (10) then "123456" (6). The preceding normal paragraph ("Hi.")
        // is still its own chunk, untouched by the fallback.
        String text = "Hi.\n\n1234567890123456";

        List<String> chunks = new ParagraphChunker(10).chunk(text);

        assertThat(chunks).containsExactly("Hi.", "1234567890", "123456");
    }

    @Test
    void chunk_whenAParagraphExactlyEqualsMaxChunkSize_isNotTreatedAsOversized() {
        String text = "Testing.";

        List<String> chunks = new ParagraphChunker(8).chunk(text);

        assertThat(chunks).containsExactly("Testing.");
    }

    @Test
    void chunk_withNoBlankLines_treatsWholeTextAsOneParagraph() {
        String text = "Distributed systems require reliable coordination.";

        List<String> chunks = new ParagraphChunker(500).chunk(text);

        assertThat(chunks).containsExactly("Distributed systems require reliable coordination.");
    }

    @Test
    void chunk_collapsesMultipleConsecutiveBlankLinesIntoOneBoundary() {
        // Three newlines between paragraphs (i.e. two blank lines) must still be read
        // as a single paragraph boundary, not produce an empty chunk in between.
        String text = "Para one.\n\n\nPara two.";

        List<String> chunks = new ParagraphChunker(500).chunk(text);

        assertThat(chunks).containsExactly("Para one.", "Para two.");
    }

    @Test
    void chunk_whenTextIsNull_throwsNullPointerException() {
        assertThatThrownBy(() -> new ParagraphChunker(10).chunk(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("text must not be null");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void constructor_whenMaxChunkSizeIsZeroOrNegative_throwsIllegalArgumentException(int invalidMaxChunkSize) {
        assertThatThrownBy(() -> new ParagraphChunker(invalidMaxChunkSize))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxChunkSize must be positive");
    }

    @Test
    void chunk_withRealDisputePolicyDocument_atAGenerousBound_preservesAllFourParagraphsCleanly() {
        // Real, measured lengths of this document's 4 blank-line-delimited paragraphs
        // (title + 3 body paragraphs): 75, 847, 622, 861 chars. All fit under 1000, so
        // at this bound every paragraph survives as exactly one chunk - the "clean
        // path" this strategy exists to demonstrate, proven on real text, not just the
        // small hand-crafted cases above.
        String document = loadResource("/documents/cardholder-dispute-policy.txt");

        List<String> chunks = new ParagraphChunker(1000).chunk(document);

        assertThat(chunks).hasSize(4);
        assertThat(chunks).extracting(String::length).containsExactly(75, 847, 622, 861);
    }

    @Test
    void chunk_withRealDisputePolicyDocument_atARealisticBound_fallbackDominatesOverCleanParagraphs() {
        // The concrete surfacing of the methodological concern: this corpus has only
        // 3 real body paragraphs, and every one of them (847/622/861 chars) exceeds a
        // realistic 300-char bound - the same bound BoundedSentenceChunkerTest already
        // uses for this document. Only the short title paragraph (75 chars) survives
        // whole; the other 3 all go through the hard-split fallback, producing 9 more
        // pieces (ceil(847/300)=3, ceil(622/300)=3, ceil(861/300)=3) for 10 total. At
        // this realistic chunk size, on this real document, "paragraph chunking" is
        // mostly just blind character-cutting wearing a paragraph-shaped label - this
        // 3-paragraph corpus alone cannot demonstrate this strategy's actual selling
        // point (whole paragraphs as retrieval units) at sizes a real RAG pipeline
        // would use. That's real evidence the comparison harness needs a second,
        // richer/shorter-paragraphed document before concluding anything about this
        // strategy from this corpus alone.
        String document = loadResource("/documents/cardholder-dispute-policy.txt");
        int maxChunkSize = 300;

        List<String> chunks = new ParagraphChunker(maxChunkSize).chunk(document);

        assertThat(chunks).hasSize(10);
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
