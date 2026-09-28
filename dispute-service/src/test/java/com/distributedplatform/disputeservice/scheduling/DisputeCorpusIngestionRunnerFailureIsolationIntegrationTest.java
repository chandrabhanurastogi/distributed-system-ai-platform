package com.distributedplatform.disputeservice.scheduling;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves one bad file in a run doesn't block a good one - and does it in a way that
 * sidesteps a real Spring test-transaction-propagation pitfall, not a production
 * concern. This test class is @Transactional for the usual per-test isolation, which
 * means the ENTIRE test method - including both files' processing - runs inside one
 * shared transaction (reingestFromSource's own @Transactional just participates in it,
 * per default REQUIRED propagation, rather than starting an independent one). If the
 * bad file's failure happened INSIDE reingestFromSource's transactional boundary,
 * Spring would mark that shared transaction rollback-only the moment the exception
 * crossed the AOP proxy - poisoning it for the rest of the test, including the good
 * file's otherwise-successful write, regardless of the runner's own try/catch already
 * having isolated the failure at the application level. The runner avoids this by
 * design: invalid UTF-8 bytes fail at Files.readString, before reingestFromSource is
 * ever called for that file, so no transaction is ever put at risk. In real
 * production (no wrapping test transaction) this distinction doesn't exist - each
 * scheduled run's per-file calls are already independent, top-level transactions.
 */
@SpringBootTest
@Testcontainers
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class DisputeCorpusIngestionRunnerFailureIsolationIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private DisputeCorpusIngestionRunner runner;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Test
    void run_withOneGoodFileAndOneInvalidUtf8File_ingestsTheGoodOneAndReportsTheBadOneAsFailed(
            @TempDir Path sourceDirectory, CapturedOutput output) throws IOException {
        Files.writeString(sourceDirectory.resolve("good-policy.txt"),
                "This is a valid, real policy document that should ingest successfully.");

        // Deliberately invalid UTF-8: 0xC3 starts a two-byte sequence that must be
        // followed by a continuation byte in the 0x80-0xBF range - 0x28 ('(') isn't
        // one, so Files.readString throws MalformedInputException on this file.
        Files.write(sourceDirectory.resolve("bad-policy.txt"), new byte[]{(byte) 0xC3, 0x28});

        IngestionRunSummary summary = runner.run(sourceDirectory);

        assertThat(summary.succeededCount()).isEqualTo(1);
        assertThat(summary.skippedCount()).isZero();
        assertThat(summary.failedCount()).isEqualTo(1);
        assertThat(summary.failedFiles()).containsExactly("bad-policy.txt");

        Long goodSourceDocumentId = jdbcTemplate.queryForObject(
                "SELECT id FROM source_documents WHERE source_identifier = :sourceIdentifier",
                new MapSqlParameterSource("sourceIdentifier", "good-policy.txt"),
                Long.class);
        assertThat(goodSourceDocumentId).isNotNull();

        Integer goodChunkCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dispute_documents WHERE source_document_id = :sourceDocumentId",
                new MapSqlParameterSource("sourceDocumentId", goodSourceDocumentId),
                Integer.class);
        assertThat(goodChunkCount).isEqualTo(1);

        Integer badSourceDocumentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM source_documents WHERE source_identifier = :sourceIdentifier",
                new MapSqlParameterSource("sourceIdentifier", "bad-policy.txt"),
                Integer.class);
        assertThat(badSourceDocumentCount).isZero();

        // Real captured console output, verified against the actual structured-logging
        // format rather than assumed: Spring Boot's structured JSON encoder promotes
        // the MDC entry to its own top-level field ("ingestionRunId":"<uuid>"), not
        // just embedding it inside the message text - both are checked here, tied to
        // the real summary object rather than duplicated magic numbers.
        assertThat(output).contains("\"ingestionRunId\":\"" + summary.ingestionRunId() + "\"");
        assertThat(output).contains("Ingestion run complete: runId=" + summary.ingestionRunId()
                + ", succeeded=" + summary.succeededCount()
                + ", skipped=" + summary.skippedCount()
                + ", failed=" + summary.failedCount());
    }
}
