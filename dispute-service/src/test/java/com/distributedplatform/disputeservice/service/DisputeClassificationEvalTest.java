package com.distributedplatform.disputeservice.service;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Not a correctness gate - a real, honest baseline measurement (Rule 9: no fabricated
 * numbers), same discipline as LlmProviderComparisonTest. Runs every claim in the
 * golden dataset through the real classify() pipeline (real Ollama at temperature 0,
 * real pgvector retrieval, the real ingested policy document) and reports the actual
 * accuracy observed - nothing assumed, nothing rounded up.
 * <p>
 * Clean and ambiguous cases are reported separately, deliberately: "ambiguous" in the
 * golden dataset means there is no single ground truth to score a hit/miss against, so
 * blending all 8 claims into one accuracy percentage would manufacture a false sense of
 * precision. Only the unambiguous claims produce the headline baseline number; the
 * ambiguous ones are logged with their full result for a human to read and judge.
 */
@SpringBootTest
@Testcontainers
@Transactional
class DisputeClassificationEvalTest {

    private static final Logger log = LoggerFactory.getLogger(DisputeClassificationEvalTest.class);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private DisputeDocumentIngestionService ingestionService;

    @Autowired
    private ClaimClassificationService classificationService;

    private record GoldenClaim(String id, String claimText, List<String> acceptableReasonCodes,
                                boolean ambiguous, String note) {
    }

    @Test
    void classify_acrossGoldenDataset_reportsRealBaselineAccuracy() throws IOException {
        String document = loadResource("/documents/cardholder-dispute-policy.txt");
        ingestionService.ingest(document);

        GoldenClaim[] goldenClaims = loadGoldenDataset();

        List<String> cleanHits = new ArrayList<>();
        List<String> cleanMisses = new ArrayList<>();

        log.info("=== Dispute classification baseline eval: {} claims ===", goldenClaims.length);

        for (GoldenClaim claim : goldenClaims) {
            ClassificationResult result = classificationService.classify(claim.claimText());
            // Substring match, not exact equality - safe only because none of today's
            // three codes (10.4, 13.1, 13.3) is a substring of another. A future code
            // that happened to be a superstring of an existing one (e.g. a hypothetical
            // "13.10" alongside "13.1") would silently false-match here. See CLAUDE.md
            // Known Existing Debt.
            boolean matched = claim.acceptableReasonCodes().stream()
                    .anyMatch(code -> result.reasonCode().contains(code));

            if (claim.ambiguous()) {
                log.info("[{}] AMBIGUOUS - acceptable={} actual={} matched={} explanation=\"{}\" note=\"{}\"",
                        claim.id(), claim.acceptableReasonCodes(), result.reasonCode(), matched,
                        result.explanation(), claim.note());
            } else {
                if (matched) {
                    cleanHits.add(claim.id());
                } else {
                    cleanMisses.add(claim.id());
                }
                log.info("[{}] CLEAN - expected={} actual={} matched={} explanation=\"{}\"",
                        claim.id(), claim.acceptableReasonCodes(), result.reasonCode(), matched, result.explanation());
            }
        }

        int cleanTotal = cleanHits.size() + cleanMisses.size();
        double accuracy = cleanTotal == 0 ? 0.0 : (double) cleanHits.size() / cleanTotal;
        log.info("=== Baseline accuracy on {} clean (unambiguous) claims: {}/{} ({}%) - hits={} misses={} ===",
                cleanTotal, cleanHits.size(), cleanTotal, Math.round(accuracy * 100), cleanHits, cleanMisses);

        // Structural assertion only - proves the harness ran every claim through the
        // real pipeline and accounted for each one. Not a correctness gate: there is
        // no prior baseline yet to hold this run's accuracy to, and manufacturing a
        // pass/fail threshold now would be exactly the fabricated-number problem
        // Rule 9 exists to prevent.
        assertThat(cleanTotal + countAmbiguous(goldenClaims)).isEqualTo(goldenClaims.length);
    }

    private long countAmbiguous(GoldenClaim[] claims) {
        return java.util.Arrays.stream(claims).filter(GoldenClaim::ambiguous).count();
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
