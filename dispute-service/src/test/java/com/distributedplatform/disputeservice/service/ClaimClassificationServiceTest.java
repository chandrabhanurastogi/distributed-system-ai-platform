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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real, end-to-end: ingests the actual policy document into a real Testcontainers
 * pgvector instance, then runs the full classify() pipeline (embed -> retrieve ->
 * prompt -> real Ollama call -> parse) against claim-01 from the golden dataset - the
 * clean, unambiguous 10.4 case. Claim text is duplicated here rather than loaded from
 * the golden-dataset JSON, deliberately: one small, low-risk string duplication versus
 * pulling in a generic-collection Jackson read whose exact API on this project's
 * relocated Jackson artifact hasn't been verified. Must be kept in sync with claim-01
 * if that entry's wording ever changes.
 */
@SpringBootTest
@Testcontainers
@Transactional
class ClaimClassificationServiceTest {

    private static final Logger log = LoggerFactory.getLogger(ClaimClassificationServiceTest.class);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private DisputeDocumentIngestionService ingestionService;

    @Autowired
    private ClaimClassificationService classificationService;

    @Test
    void classify_withRealPolicyDocumentIngested_correctlyClassifiesACleanFraudClaim() {
        String document = loadResource("/documents/cardholder-dispute-policy.txt");
        ingestionService.ingest(document);

        // golden-dataset claim-01: clean, unambiguous 10.4 case
        String claimText = "I found a $340 charge on my card from an online electronics store "
                + "I've never heard of and never shopped at. My physical card has been in my "
                + "wallet the whole time. I have never bought anything from this merchant before, ever.";

        ClassificationResult result = classificationService.classify(claimText);

        log.info("Classification result: reasonCode={}, explanation={}", result.reasonCode(), result.explanation());

        assertThat(result.reasonCode()).contains("10.4");
        assertThat(result.explanation()).isNotBlank();
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
