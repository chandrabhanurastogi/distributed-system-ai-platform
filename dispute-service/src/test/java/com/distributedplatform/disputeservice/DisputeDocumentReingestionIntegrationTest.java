package com.distributedplatform.disputeservice;

import com.distributedplatform.disputeservice.scheduling.ContentHasher;
import com.distributedplatform.disputeservice.service.DisputeDocumentIngestionService;
import com.distributedplatform.disputeservice.service.IngestionOutcome;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves Step 4's transactional idempotency design (ADR-0010) for real, across three
 * phases in one test: first ingestion, an unchanged re-run (must be a true no-op - same
 * chunk ids, same ingested_at), then a real content change (old chunk ids must be gone,
 * replaced by new ones, hash updated). Chunk ids are the load-bearing assertion here,
 * not just row counts or content: BIGSERIAL ids are strictly increasing, so "same ids
 * after re-run" is only possible if no delete+insert cycle happened at all.
 */
@SpringBootTest
@Testcontainers
@Transactional
class DisputeDocumentReingestionIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(DisputeDocumentReingestionIntegrationTest.class);
    private static final String SOURCE_IDENTIFIER = "cardholder-dispute-policy.txt";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private DisputeDocumentIngestionService ingestionService;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Test
    void reingestFromSource_acrossThreePhases_isIdempotentThenReplacesOnRealChange() {
        String originalText = "Version one of the cardholder dispute policy.";
        String originalHash = ContentHasher.sha256Hex(originalText);

        // Phase 1: first-ever ingestion of this source
        IngestionOutcome firstIngestOutcome =
                ingestionService.reingestFromSource(SOURCE_IDENTIFIER, originalText, originalHash);
        assertThat(firstIngestOutcome).isEqualTo(IngestionOutcome.INGESTED);

        Long sourceDocumentId = findSourceDocumentId(SOURCE_IDENTIFIER);
        assertThat(sourceDocumentId).isNotNull();
        assertThat(findContentHash(sourceDocumentId)).isEqualTo(originalHash);

        List<Long> chunkIdsAfterFirstIngest = findChunkIds(sourceDocumentId);
        assertThat(chunkIdsAfterFirstIngest).isNotEmpty();
        LocalDateTime ingestedAtAfterFirstIngest = findIngestedAt(sourceDocumentId);

        log.info("Phase 1 (first ingest): sourceDocumentId={}, chunkIds={}", sourceDocumentId, chunkIdsAfterFirstIngest);

        // Phase 2: re-run with unchanged content - must be a true no-op
        IngestionOutcome unchangedRerunOutcome =
                ingestionService.reingestFromSource(SOURCE_IDENTIFIER, originalText, originalHash);
        assertThat(unchangedRerunOutcome).isEqualTo(IngestionOutcome.SKIPPED_UNCHANGED);

        List<Long> chunkIdsAfterUnchangedRerun = findChunkIds(sourceDocumentId);
        assertThat(chunkIdsAfterUnchangedRerun).containsExactlyInAnyOrderElementsOf(chunkIdsAfterFirstIngest);
        assertThat(findIngestedAt(sourceDocumentId)).isEqualTo(ingestedAtAfterFirstIngest);

        log.info("Phase 2 (unchanged re-run): chunkIds unchanged, ingestedAt unchanged");

        // Phase 3: the file actually changes - old chunks must be replaced
        String newText = "Version two of the cardholder dispute policy, completely rewritten.";
        String newHash = ContentHasher.sha256Hex(newText);
        assertThat(newHash).isNotEqualTo(originalHash);

        IngestionOutcome realChangeOutcome =
                ingestionService.reingestFromSource(SOURCE_IDENTIFIER, newText, newHash);
        assertThat(realChangeOutcome).isEqualTo(IngestionOutcome.INGESTED);

        assertThat(findContentHash(sourceDocumentId)).isEqualTo(newHash);

        List<Long> chunkIdsAfterRealChange = findChunkIds(sourceDocumentId);
        assertThat(chunkIdsAfterRealChange).isNotEmpty();
        assertThat(chunkIdsAfterRealChange).doesNotContainAnyElementsOf(chunkIdsAfterFirstIngest);

        String newChunkText = jdbcTemplate.queryForObject(
                "SELECT text FROM dispute_documents WHERE source_document_id = :sourceDocumentId",
                new MapSqlParameterSource("sourceDocumentId", sourceDocumentId),
                String.class);
        assertThat(newChunkText).isEqualTo(newText);

        log.info("Phase 3 (real change): old chunkIds={} gone, new chunkIds={}",
                chunkIdsAfterFirstIngest, chunkIdsAfterRealChange);
    }

    private Long findSourceDocumentId(String sourceIdentifier) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM source_documents WHERE source_identifier = :sourceIdentifier",
                new MapSqlParameterSource("sourceIdentifier", sourceIdentifier),
                Long.class);
    }

    private String findContentHash(Long sourceDocumentId) {
        return jdbcTemplate.queryForObject(
                "SELECT content_hash FROM source_documents WHERE id = :id",
                new MapSqlParameterSource("id", sourceDocumentId),
                String.class);
    }

    private LocalDateTime findIngestedAt(Long sourceDocumentId) {
        return jdbcTemplate.queryForObject(
                "SELECT ingested_at FROM source_documents WHERE id = :id",
                new MapSqlParameterSource("id", sourceDocumentId),
                LocalDateTime.class);
    }

    private List<Long> findChunkIds(Long sourceDocumentId) {
        return jdbcTemplate.query(
                "SELECT id FROM dispute_documents WHERE source_document_id = :sourceDocumentId ORDER BY id",
                new MapSqlParameterSource("sourceDocumentId", sourceDocumentId),
                (rs, rowNum) -> rs.getLong("id"));
    }
}
