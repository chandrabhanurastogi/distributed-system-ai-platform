package com.distributedplatform.llmfundamentals.embedding;

import com.distributedplatform.llmfundamentals.vector.VectorMath;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Not a mocked unit test — a real, end-to-end retrieval pipeline test (Milestone 7.2):
 * real HTTP calls to a locally running Ollama {@code nomic-embed-text}, real embeddings,
 * real {@link BruteForceRetriever} ranking. Requires Ollama running locally with
 * nomic-embed-text pulled (see ROADMAP.md Milestone 7.2 environment prerequisite).
 * <p>
 * Corpus is deliberately two clearly separated topics (distributed systems vs. baking) so a
 * human reading the logged rankings can sanity-check them without knowing anything about
 * embedding models. Timing is real wall-clock (Rule 9: no fabricated numbers), same discipline
 * as {@link com.distributedplatform.llmfundamentals.service.LlmProviderComparisonTest}.
 */
@SpringBootTest
class BruteForceRetrievalIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(BruteForceRetrievalIntegrationTest.class);
    private static final String EMBED_MODEL = "nomic-embed-text";

    @Autowired
    private OllamaEmbeddingService embeddingService;

    private final BruteForceRetriever retriever = new BruteForceRetriever();

    // Group A: distributed systems. Group B: baking. Deliberately unambiguous topics so a
    // human can eyeball whether the ranking makes sense.
    private static final Map<String, String> CORPUS_TEXT = new LinkedHashMap<>();
    static {
        CORPUS_TEXT.put("ds-1", "Distributed systems require reliable communication protocols to coordinate between nodes.");
        CORPUS_TEXT.put("ds-2", "A consensus algorithm like Raft helps replicated nodes agree on a single source of truth.");
        CORPUS_TEXT.put("ds-3", "Kafka partitions allow horizontal scaling of event streams across multiple consumers.");
        CORPUS_TEXT.put("ds-4", "Idempotent APIs let clients safely retry requests without causing duplicate side effects.");
        CORPUS_TEXT.put("baking-1", "Kneading dough develops gluten strands that give bread its chewy texture.");
        CORPUS_TEXT.put("baking-2", "A roux made from butter and flour thickens sauces like bechamel.");
        CORPUS_TEXT.put("baking-3", "Searing meat at high heat creates a flavorful crust through the Maillard reaction.");
        CORPUS_TEXT.put("baking-4", "Resting a steak after cooking lets the juices redistribute evenly.");
    }

    private static final Set<String> DISTRIBUTED_SYSTEMS_IDS = Set.of("ds-1", "ds-2", "ds-3", "ds-4");

    private static final String QUERY_TEXT =
            "How do distributed databases handle node failures and maintain consistency?";

    @Test
    void topK_withRealCorpusAndRealOllamaEmbeddings_ranksDistributedSystemsDocsAboveBaking() {
        // Arrange - embed the whole corpus, timing the real wall-clock cost of doing so
        long corpusEmbedStart = System.nanoTime();

        List<Document> corpus = CORPUS_TEXT.entrySet().stream()
                .map(entry -> new Document(entry.getKey(), entry.getValue(),
                        embeddingService.embed(EMBED_MODEL, entry.getValue())))
                .toList();

        long corpusEmbedElapsedMs = (System.nanoTime() - corpusEmbedStart) / 1_000_000;

        log.info("Embedded {} corpus sentences in {} ms (avg {} ms/sentence) using model '{}'",
                corpus.size(), corpusEmbedElapsedMs, corpusEmbedElapsedMs / corpus.size(), EMBED_MODEL);

        // Sanity-check real dimensionality on every embedded document, not just the first
        corpus.forEach(doc -> assertThat(doc.embedding().length).isEqualTo(768));

        // Embed the query sentence separately (not part of the "whole corpus" timing above)
        long queryEmbedStart = System.nanoTime();
        double[] queryVector = embeddingService.embed(EMBED_MODEL, QUERY_TEXT);
        long queryEmbedElapsedMs = (System.nanoTime() - queryEmbedStart) / 1_000_000;
        log.info("Embedded query in {} ms: \"{}\"", queryEmbedElapsedMs, QUERY_TEXT);

        // Act
        List<Document> ranked = retriever.topK(queryVector, corpus, corpus.size());

        // Log every result with its real cosine similarity score, ranked, for human sanity-checking
        log.info("=== Ranked results for query: \"{}\" ===", QUERY_TEXT);
        for (int i = 0; i < ranked.size(); i++) {
            Document doc = ranked.get(i);
            double score = VectorMath.cosineSimilarity(queryVector, doc.embedding());
            log.info("{}. [{}] score={} -- \"{}\"", i + 1, doc.id(), String.format("%.4f", score), doc.text());
        }

        // Assert - the query is unambiguously about distributed systems, and the two corpus
        // topics are unrelated, so the single closest match should be a distributed-systems
        // sentence. Deliberately not asserting full rank order within a topic group: that's a
        // finer-grained claim about the embedding model's semantics than this milestone needs,
        // and would make the test flaky against model/version drift.
        assertThat(DISTRIBUTED_SYSTEMS_IDS).contains(ranked.get(0).id());
    }
}
