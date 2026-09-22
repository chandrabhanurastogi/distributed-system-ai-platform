package com.distributedplatform.llmfundamentals.embedding;

import com.distributedplatform.llmfundamentals.vector.ZeroVectorException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BruteForceRetrieverTest {

    private final BruteForceRetriever retriever = new BruteForceRetriever();

    @Test
    void topK_withHandCraftedVectors_ranksCorrectlyByDescendingSimilarity() {
        // Query vector pointing along the positive X-axis
        double[] queryVector = {1.0, 0.0};

        // Hand-crafted documents:
        // doc1: parallel to query -> similarity = 1.0
        // doc2: 45 degrees to query -> similarity ≈ 0.707
        // doc3: 90 degrees / orthogonal -> similarity = 0.0
        // doc4: 180 degrees / opposite -> similarity = -1.0
        Document doc1 = new Document("doc-1", "Perfect match", new double[]{2.0, 0.0});
        Document doc2 = new Document("doc-2", "Partial match", new double[]{1.0, 1.0});
        Document doc3 = new Document("doc-3", "Unrelated", new double[]{0.0, 1.0});
        Document doc4 = new Document("doc-4", "Opposite", new double[]{-1.0, 0.0});

        List<Document> corpus = List.of(doc3, doc1, doc4, doc2);

        // Retrieve top 2
        List<Document> top2 = retriever.topK(queryVector, corpus, 2);
        assertThat(top2).containsExactly(doc1, doc2);

        // Retrieve top 3
        List<Document> top3 = retriever.topK(queryVector, corpus, 3);
        assertThat(top3).containsExactly(doc1, doc2, doc3);
    }

    @Test
    void topK_whenKGreaterThanCorpusSize_returnsAllDocumentsInRankedOrder() {
        double[] queryVector = {1.0, 0.0};

        Document doc1 = new Document("doc-1", "Match", new double[]{1.0, 0.0});
        Document doc2 = new Document("doc-2", "Opposite", new double[]{-1.0, 0.0});
        List<Document> corpus = List.of(doc2, doc1);

        List<Document> result = retriever.topK(queryVector, corpus, 10);

        assertThat(result).containsExactly(doc1, doc2);
    }

    @Test
    void topK_whenCorpusIsEmpty_returnsEmptyList() {
        double[] queryVector = {1.0, 0.0};

        List<Document> result = retriever.topK(queryVector, List.of(), 5);

        assertThat(result).isEmpty();
    }

    @Test
    void topK_whenCorpusIsNull_returnsEmptyList() {
        double[] queryVector = {1.0, 0.0};

        List<Document> result = retriever.topK(queryVector, null, 5);

        assertThat(result).isEmpty();
    }

    @Test
    void topK_whenKIsZeroOrNegative_returnsEmptyList() {
        double[] queryVector = {1.0, 0.0};
        Document doc = new Document("doc-1", "Text", new double[]{1.0, 0.0});
        List<Document> corpus = List.of(doc);

        assertThat(retriever.topK(queryVector, corpus, 0)).isEmpty();
        assertThat(retriever.topK(queryVector, corpus, -1)).isEmpty();
    }

    @Test
    void topK_whenQueryVectorIsNull_throwsNullPointerException() {
        Document doc = new Document("doc-1", "Text", new double[]{1.0, 0.0});
        List<Document> corpus = List.of(doc);

        assertThatThrownBy(() -> retriever.topK(null, corpus, 1))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("queryVector must not be null");
    }

    @Test
    void topK_whenQueryVectorIsZeroVector_throwsZeroVectorException() {
        // Proves that ZeroVectorException propagates cleanly out of the stream pipeline
        double[] zeroQuery = {0.0, 0.0};
        Document doc = new Document("doc-1", "Sample text", new double[]{1.0, 2.0});
        List<Document> corpus = List.of(doc);

        assertThatThrownBy(() -> retriever.topK(zeroQuery, corpus, 1))
                .isInstanceOf(ZeroVectorException.class)
                .hasMessage("Vector must not be a zero vector (magnitude is 0)");
    }

    @Test
    void topK_whenCorpusContainsZeroVector_throwsZeroVectorException() {
        // Proves that a degenerate document embedding in the corpus also throws ZeroVectorException
        double[] validQuery = {1.0, 0.0};
        Document validDoc = new Document("doc-1", "Valid doc", new double[]{1.0, 0.0});
        Document zeroDoc = new Document("doc-2", "Corrupted zero doc", new double[]{0.0, 0.0});
        List<Document> corpus = List.of(validDoc, zeroDoc);

        assertThatThrownBy(() -> retriever.topK(validQuery, corpus, 2))
                .isInstanceOf(ZeroVectorException.class)
                .hasMessage("Vector must not be a zero vector (magnitude is 0)");
    }
}
