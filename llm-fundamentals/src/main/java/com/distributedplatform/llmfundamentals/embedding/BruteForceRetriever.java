package com.distributedplatform.llmfundamentals.embedding;

import com.distributedplatform.llmfundamentals.vector.VectorMath;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class BruteForceRetriever {

    public List<Document> topK(double[] queryVector, List<Document> corpus, int k) {
        Objects.requireNonNull(queryVector, "queryVector must not be null");

        if (corpus == null || corpus.isEmpty() || k <= 0) {
            return List.of();
        }

        record ScoredDocument(Document document, double score) {}

        return corpus.stream()
                .map(doc -> new ScoredDocument(doc, VectorMath.cosineSimilarity(queryVector, doc.embedding())))
                .sorted(Comparator.comparingDouble(ScoredDocument::score).reversed())
                .limit(k)
                .map(ScoredDocument::document)
                .toList();
    }
}
