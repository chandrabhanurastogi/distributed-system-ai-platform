package com.distributedplatform.disputeservice.chunking;

import com.distributedplatform.disputeservice.embedding.OllamaEmbeddingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SemanticChunkerTest {

    private static final String EMBEDDING_MODEL = "nomic-embed-text";

    @Mock
    private OllamaEmbeddingService embeddingService;

    @Test
    void chunk_withAClearTopicShift_splitsIntoTwoGroupsAtTheBreakpoint() {
        // Two "cat" sentences pointing the same direction ([1,0]), two "stock market"
        // sentences pointing the orthogonal direction ([0,1]). Distances: 0, 1, 0 -
        // a single, obvious spike between sentence 2 and 3. At breakpointPercentile=0.5
        // (deliberately lower than the textbook 0.95 default - see the real-corpus
        // tests below for why that matters at small sample sizes), the threshold lands
        // on 0, so the spike (1 > 0) is the one real breakpoint.
        String catA = "Cats are great pets.";
        String catB = "Cats like to nap often.";
        String marketA = "Stock markets fell sharply today.";
        String marketB = "Investors reacted with alarm.";
        String text = catA + " " + catB + " " + marketA + " " + marketB;

        when(embeddingService.embed(eq(EMBEDDING_MODEL), eq(catA))).thenReturn(new double[]{1.0, 0.0});
        when(embeddingService.embed(eq(EMBEDDING_MODEL), eq(catB))).thenReturn(new double[]{1.0, 0.0});
        when(embeddingService.embed(eq(EMBEDDING_MODEL), eq(marketA))).thenReturn(new double[]{0.0, 1.0});
        when(embeddingService.embed(eq(EMBEDDING_MODEL), eq(marketB))).thenReturn(new double[]{0.0, 1.0});

        List<String> chunks = new SemanticChunker(embeddingService, 500, 0.5).chunk(text);

        assertThat(chunks).containsExactly(catA + " " + catB, marketA + " " + marketB);
    }

    @Test
    void chunk_withAnOversizedSemanticGroup_repacksAtSentenceLevelRatherThanBlindCutting() {
        // Both sentences point the same direction ([1,0]) - one semantic group - but
        // together (19 chars) they exceed maxChunkSize=15, so the group gets re-packed
        // at the sentence level: "One fish."(9) alone, then "Two fish."(9) alone,
        // rather than a blind character cut that would have ignored the sentence
        // boundary sitting right there.
        String sentenceA = "One fish.";
        String sentenceB = "Two fish.";
        String text = sentenceA + " " + sentenceB;

        when(embeddingService.embed(eq(EMBEDDING_MODEL), eq(sentenceA))).thenReturn(new double[]{1.0, 0.0});
        when(embeddingService.embed(eq(EMBEDDING_MODEL), eq(sentenceB))).thenReturn(new double[]{1.0, 0.0});

        List<String> chunks = new SemanticChunker(embeddingService, 15, 0.5).chunk(text);

        assertThat(chunks).containsExactly(sentenceA, sentenceB);
    }

    @Test
    void chunk_withASingleSentenceTooLongToPack_splitsIntoWordsWithoutEverCallingTheEmbeddingService() {
        // With only one sentence, there's nothing to compare, so embedding is skipped
        // entirely - not just "no breakpoint found," but genuinely zero calls. The
        // oversized sentence then falls straight to word-level packing, same math as
        // RecursiveChunkerTest's equivalent case.
        String text = "Alpha beta gamma delta.";

        List<String> chunks = new SemanticChunker(embeddingService, 10, 0.95).chunk(text);

        assertThat(chunks).containsExactly("Alpha beta", "gamma", "delta.");
        verifyNoInteractions(embeddingService);
    }

    @Test
    void chunk_withAWordLongerThanMaxChunkSize_hardSplitsItByCharacterCount() {
        String text = "Supercalifragilisticexpialidocious";

        List<String> chunks = new SemanticChunker(embeddingService, 10, 0.95).chunk(text);

        assertThat(chunks).containsExactly("Supercalif", "ragilistic", "expialidoc", "ious");
        verifyNoInteractions(embeddingService);
    }

    @Test
    void chunk_withAZeroMagnitudeEmbedding_throwsZeroVectorExceptionRatherThanProducingNaN() {
        // Mirrors ADR-0008's own decision for the identical undefined operation: a
        // degenerate [0,0] embedding (e.g. a corrupted or empty-input embedding call)
        // must fail loudly here, not silently turn into NaN and corrupt the
        // percentile-threshold computation with no exception and no log line.
        String sentenceA = "One fish.";
        String sentenceB = "Two fish.";
        String text = sentenceA + " " + sentenceB;

        when(embeddingService.embed(eq(EMBEDDING_MODEL), eq(sentenceA))).thenReturn(new double[]{0.0, 0.0});
        when(embeddingService.embed(eq(EMBEDDING_MODEL), eq(sentenceB))).thenReturn(new double[]{1.0, 0.0});

        assertThatThrownBy(() -> new SemanticChunker(embeddingService, 500, 0.5).chunk(text))
                .isInstanceOf(ZeroVectorException.class)
                .hasMessage("Cosine similarity is undefined for a zero-magnitude sentence embedding");
    }

    @Test
    void chunk_whenTextIsNull_throwsNullPointerException() {
        assertThatThrownBy(() -> new SemanticChunker(embeddingService, 10, 0.95).chunk(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("text must not be null");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void constructor_whenMaxChunkSizeIsZeroOrNegative_throwsIllegalArgumentException(int invalidMaxChunkSize) {
        assertThatThrownBy(() -> new SemanticChunker(embeddingService, invalidMaxChunkSize, 0.95))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxChunkSize must be positive");
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 1.1})
    void constructor_whenBreakpointPercentileIsOutOfRange_throwsIllegalArgumentException(double invalidPercentile) {
        assertThatThrownBy(() -> new SemanticChunker(embeddingService, 10, invalidPercentile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("breakpointPercentile must be between 0.0 and 1.0");
    }

    @Test
    void chunk_withRealDisputePolicyDocument_atTheDefaultPercentile_missesTheRealSemanticShift() {
        // The honest negative result flagged before implementation: this document's
        // whole-document sentence split has only 10 sentences (9 consecutive-sentence
        // distances). Embeddings below are mocked, hand-assigned to the real
        // sentences in call order - sentence 0/1 point the same direction ([1,0]),
        // sentences 2-9 all point the orthogonal direction ([0,1]) - a single, clean,
        // deliberate shift after sentence 1. At the textbook-standard
        // breakpointPercentile=0.95 (the app-config default), the threshold computed
        // over only 9 distances lands on the sample's own maximum, so even this
        // obvious a shift can't exceed it - real evidence, not speculation, that this
        // corpus can't demonstrate the default's real behavior. The document still
        // gets packed into real chunks (one undivided group, capped at
        // maxChunkSize=1000): 3 chunks, computed via a direct Python port of this
        // exact algorithm - not guessed.
        String document = loadResource("/documents/cardholder-dispute-policy.txt");
        when(embeddingService.embed(eq(EMBEDDING_MODEL), anyString())).thenReturn(
                new double[]{1.0, 0.0}, new double[]{1.0, 0.0},
                new double[]{0.0, 1.0}, new double[]{0.0, 1.0}, new double[]{0.0, 1.0},
                new double[]{0.0, 1.0}, new double[]{0.0, 1.0}, new double[]{0.0, 1.0},
                new double[]{0.0, 1.0}, new double[]{0.0, 1.0});

        List<String> chunks = new SemanticChunker(embeddingService, 1000, 0.95).chunk(document);

        assertThat(chunks).extracting(String::length).containsExactly(975, 900, 532);
    }

    @Test
    void chunk_withRealDisputePolicyDocument_atALowerPercentile_catchesTheSameRealShift() {
        // Identical real document, identical mocked embeddings and real semantic
        // shift as the test above - only breakpointPercentile differs (0.5 here,
        // passed directly to the constructor, not changed as the app-wide default).
        // This time the shift is caught: 2 groups instead of 1, which forces an
        // earlier flush than the greedy packer would have chosen on its own -
        // concretely proving the percentile parameter is mechanically load-bearing,
        // not cosmetic. 4 chunks, same Python-verified algorithm.
        String document = loadResource("/documents/cardholder-dispute-policy.txt");
        when(embeddingService.embed(eq(EMBEDDING_MODEL), anyString())).thenReturn(
                new double[]{1.0, 0.0}, new double[]{1.0, 0.0},
                new double[]{0.0, 1.0}, new double[]{0.0, 1.0}, new double[]{0.0, 1.0},
                new double[]{0.0, 1.0}, new double[]{0.0, 1.0}, new double[]{0.0, 1.0},
                new double[]{0.0, 1.0}, new double[]{0.0, 1.0});

        List<String> chunks = new SemanticChunker(embeddingService, 1000, 0.5).chunk(document);

        assertThat(chunks).extracting(String::length).containsExactly(338, 946, 875, 247);
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
