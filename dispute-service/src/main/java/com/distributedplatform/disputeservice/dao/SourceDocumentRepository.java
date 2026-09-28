package com.distributedplatform.disputeservice.dao;

import com.distributedplatform.disputeservice.SourceDocument;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class SourceDocumentRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SourceDocumentRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<SourceDocument> findBySourceIdentifier(String sourceIdentifier) {
        String sql = """
                SELECT id, source_identifier, content_hash, ingested_at
                FROM source_documents
                WHERE source_identifier = :sourceIdentifier
                """;
        MapSqlParameterSource params = new MapSqlParameterSource("sourceIdentifier", sourceIdentifier);

        List<SourceDocument> results = jdbcTemplate.query(sql, params, (rs, rowNum) -> new SourceDocument(
                rs.getLong("id"),
                rs.getString("source_identifier"),
                rs.getString("content_hash"),
                rs.getTimestamp("ingested_at").toLocalDateTime()
        ));

        return results.stream().findFirst();
    }

    public Long insert(SourceDocument document) {
        String sql = """
                INSERT INTO source_documents (source_identifier, content_hash, ingested_at)
                VALUES (:sourceIdentifier, :contentHash, :ingestedAt)
                RETURNING id
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("sourceIdentifier", document.sourceIdentifier())
                .addValue("contentHash", document.contentHash())
                .addValue("ingestedAt", document.ingestedAt());

        return jdbcTemplate.queryForObject(sql, params, Long.class);
    }

    public int updateHash(Long id, String contentHash, LocalDateTime ingestedAt) {
        String sql = """
                UPDATE source_documents
                SET content_hash = :contentHash, ingested_at = :ingestedAt
                WHERE id = :id
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("contentHash", contentHash)
                .addValue("ingestedAt", ingestedAt);

        return jdbcTemplate.update(sql, params);
    }
}
