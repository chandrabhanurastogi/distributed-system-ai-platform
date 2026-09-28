package com.distributedplatform.disputeservice.scheduling;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
 * Closes a real gap: reingestFromSource used to return void, so
 * DisputeCorpusIngestionRunner had no way to distinguish "freshly re-embedded" from
 * "skipped, unchanged" - both looked identical from the outside (succeeded=N,
 * failed=0). DisputeDocumentReingestionIntegrationTest already proved the underlying
 * data-level behavior (same chunk ids, same ingested_at) by calling reingestFromSource
 * directly, but never went through the runner, so it never proved the OBSERVABLE
 * summary/log surface actually reports a skip. This test does: same source directory,
 * same file, unchanged between two real runs of the runner itself.
 */
@SpringBootTest
@Testcontainers
@Transactional
class DisputeCorpusIngestionRunnerSkipTrackingIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private DisputeCorpusIngestionRunner runner;

    @Test
    void run_twiceWithNoChangeToTheFile_reportsIngestedThenSkippedInTheObservableSummary(
            @TempDir Path sourceDirectory) throws IOException {
        Files.writeString(sourceDirectory.resolve("stable-policy.txt"),
                "This policy document never changes between the two runs.");

        IngestionRunSummary firstRun = runner.run(sourceDirectory);

        assertThat(firstRun.succeededCount()).isEqualTo(1);
        assertThat(firstRun.skippedCount()).isZero();
        assertThat(firstRun.failedCount()).isZero();

        IngestionRunSummary secondRun = runner.run(sourceDirectory);

        assertThat(secondRun.succeededCount()).isZero();
        assertThat(secondRun.skippedCount()).isEqualTo(1);
        assertThat(secondRun.failedCount()).isZero();

        // Different runs, different runIds - a genuinely different run correctly
        // recognized the file as unchanged, not the same run somehow counted twice.
        assertThat(secondRun.ingestionRunId()).isNotEqualTo(firstRun.ingestionRunId());
    }
}
