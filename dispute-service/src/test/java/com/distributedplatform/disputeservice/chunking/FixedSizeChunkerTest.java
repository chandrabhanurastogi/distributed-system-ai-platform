package com.distributedplatform.disputeservice.chunking;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FixedSizeChunkerTest {

    @Test
    void chunk_withTextLongerThanChunkSize_cutsEveryNCharactersIgnoringWordBoundaries() {
        // "Hello world" is 11 chars; chunkSize=5 cuts blindly: "Hello", " worl", "d" -
        // the cut lands mid-word on purpose, proving there's no word-boundary
        // awareness at all, unlike BoundedSentenceChunker.
        String text = "Hello world";

        List<String> chunks = new FixedSizeChunker(5).chunk(text);

        assertThat(chunks).containsExactly("Hello", " worl", "d");
    }

    @Test
    void chunk_withTextExactMultipleOfChunkSize_producesNoEmptyTrailingChunk() {
        String text = "12345678"; // exactly 2 * 4

        List<String> chunks = new FixedSizeChunker(4).chunk(text);

        assertThat(chunks).containsExactly("1234", "5678");
    }

    @Test
    void chunk_withTextShorterThanChunkSize_returnsItAsASingleChunk() {
        String text = "Hi";

        List<String> chunks = new FixedSizeChunker(500).chunk(text);

        assertThat(chunks).containsExactly("Hi");
    }

    @Test
    void chunk_withTextExactlyEqualToChunkSize_returnsItAsASingleChunk() {
        String text = "Testing.";

        List<String> chunks = new FixedSizeChunker(8).chunk(text);

        assertThat(chunks).containsExactly("Testing.");
    }

    @Test
    void chunk_withEmptyText_returnsEmptyList() {
        List<String> chunks = new FixedSizeChunker(10).chunk("");

        assertThat(chunks).isEmpty();
    }

    @Test
    void chunk_whenTextIsNull_throwsNullPointerException() {
        assertThatThrownBy(() -> new FixedSizeChunker(10).chunk(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("text must not be null");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void constructor_whenChunkSizeIsZeroOrNegative_throwsIllegalArgumentException(int invalidChunkSize) {
        assertThatThrownBy(() -> new FixedSizeChunker(invalidChunkSize))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("chunkSize must be positive");
    }
}
