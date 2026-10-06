package com.distributedplatform.disputeservice.service;

import com.distributedplatform.disputeservice.DisputeDocument;
import com.distributedplatform.disputeservice.chunking.ChunkingStrategy;
import com.distributedplatform.disputeservice.dao.DisputeDocumentRepository;
import com.distributedplatform.disputeservice.embedding.OllamaEmbeddingService;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Milestone 8.5's actual payoff: Milestone 8.4 built the yardstick (the golden
 * dataset + classify() pipeline), and this is the first time it's used to compare
 * more than one strategy for the same real downstream task, holding everything else
 * (embedding model, retrieval, LLM, temperature 0) fixed and varying only chunking.
 * <p>
 * Same discipline as DisputeClassificationEvalTest: not a correctness gate - a real,
 * honest measurement (Rule 9). Clean and ambiguous claims are scored separately for
 * the same reason as there - no single ground truth exists for the ambiguous ones, so
 * blending them in would manufacture false precision. Run across two real documents,
 * not one: Milestone 8.5's own ParagraphChunker/SemanticChunker tests already proved
 * empirically that the original single 3-paragraph, 10-sentence document is too small
 * to let paragraph-based or semantic chunking show their real behavior at a realistic
 * bound - this harness would inherit that same blindness with only one document.
 */
@SpringBootTest
@Testcontainers
@Transactional
class ChunkingStrategyComparisonEvalTest {

    private static final Logger log = LoggerFactory.getLogger(ChunkingStrategyComparisonEvalTest.class);
    private static final String EMBEDDING_MODEL = "nomic-embed-text";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private Map<String, ChunkingStrategy> chunkingStrategies;

    @Autowired
    private OllamaEmbeddingService embeddingService;

    @Autowired
    private DisputeDocumentRepository disputeDocumentRepository;

    @Autowired
    private ClaimClassificationService classificationService;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    private record GoldenClaim(String id, String claimText, List<String> acceptableReasonCodes,
                                boolean ambiguous, String note) {
    }

    @Test
    void classify_acrossGoldenDatasetPerChunkingStrategy_reportsRealComparativeAccuracy() throws IOException {
        List<String> documents = List.of(
                loadResource("/documents/cardholder-dispute-policy.txt"),
                loadResource("/documents/network-interchange-scheme-rules.txt"));
        GoldenClaim[] goldenClaims = loadGoldenDataset();

        log.info("=== Chunking strategy comparison: {} strategies, {} claims, {} documents ===",
                chunkingStrategies.size(), goldenClaims.length, documents.size());

        // TreeMap for deterministic, alphabetically sorted log/result order - the map
        // Spring hands back is not guaranteed to iterate in any particular order.
        Map<String, Double> accuracyByStrategy = new TreeMap<>();

        for (Map.Entry<String, ChunkingStrategy> entry : new TreeMap<>(chunkingStrategies).entrySet()) {
            String strategyName = entry.getKey();
            ChunkingStrategy strategy = entry.getValue();

            resetDisputeDocuments();
            int chunkCount = ingest(strategy, documents);

            List<String> cleanHits = new ArrayList<>();
            List<String> cleanMisses = new ArrayList<>();

            for (GoldenClaim claim : goldenClaims) {
                ClassificationResult result = classificationService.classify(claim.claimText());
                // Same substring match, same tracked one-directional risk, as
                // DisputeClassificationEvalTest - see CLAUDE.md Known Existing Debt.
                boolean matched = claim.acceptableReasonCodes().stream()
                        .anyMatch(code -> result.reasonCode().contains(code));

                if (claim.ambiguous()) {
                    log.info("[{}][{}] AMBIGUOUS - acceptable={} actual={} matched={} explanation=\"{}\"",
                            strategyName, claim.id(), claim.acceptableReasonCodes(), result.reasonCode(),
                            matched, result.explanation());
                } else {
                    if (matched) {
                        cleanHits.add(claim.id());
                    } else {
                        cleanMisses.add(claim.id());
                    }
                }
            }

            int cleanTotal = cleanHits.size() + cleanMisses.size();
            double accuracy = cleanTotal == 0 ? 0.0 : (double) cleanHits.size() / cleanTotal;
            accuracyByStrategy.put(strategyName, accuracy);

            log.info("=== [{}] chunks={} accuracy={}/{} ({}%) hits={} misses={} ===",
                    strategyName, chunkCount, cleanHits.size(), cleanTotal,
                    Math.round(accuracy * 100), cleanHits, cleanMisses);
        }

        log.info("=== Final comparison, all strategies, same golden set/retrieval/LLM: {} ===", accuracyByStrategy);

        // Structural assertion only, same Rule 9 discipline as DisputeClassificationEvalTest:
        // every strategy produced a real accuracy number, not that any number is "correct" -
        // there is no prior baseline to gate against, and inventing one now would be the
        // exact fabricated-number problem Rule 9 exists to prevent.
        assertThat(accuracyByStrategy).hasSize(chunkingStrategies.size());
    }

    private int ingest(ChunkingStrategy strategy, List<String> documents) {
        int chunkCount = 0;
        for (String document : documents) {
            for (String chunkText : strategy.chunk(document)) {
                double[] embedding = embeddingService.embed(EMBEDDING_MODEL, chunkText);
                disputeDocumentRepository.save(new DisputeDocument(chunkText, embedding));
                chunkCount++;
            }
        }
        return chunkCount;
    }

    private void resetDisputeDocuments() {
        jdbcTemplate.update("DELETE FROM dispute_documents", Collections.emptyMap());
    }

    private GoldenClaim[] loadGoldenDataset() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/golden-dataset/dispute-classification-golden-set.json")) {
            if (in == null) {
                throw new IllegalStateException("Golden dataset resource not found on classpath");
            }
            return new ObjectMapper().readValue(in, GoldenClaim[].class);
        }
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
