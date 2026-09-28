package com.distributedplatform.disputeservice.dao;

import com.distributedplatform.disputeservice.DisputeDocument;
import com.distributedplatform.disputeservice.embedding.VectorLiterals;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DisputeDocumentRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public DisputeDocumentRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long save(DisputeDocument document) {
        String sql = """
                INSERT INTO dispute_documents (text, embedding, source_document_id)
                VALUES (:text, CAST(:embedding AS vector), :sourceDocumentId)
                RETURNING id
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("text", document.text())
                .addValue("embedding", VectorLiterals.toVectorLiteral(document.embedding()))
                .addValue("sourceDocumentId", document.sourceDocumentId());

        return jdbcTemplate.queryForObject(sql, params, Long.class);
    }

    public int deleteBySourceDocumentId(Long sourceDocumentId) {
        String sql = "DELETE FROM dispute_documents WHERE source_document_id = :sourceDocumentId";
        MapSqlParameterSource params = new MapSqlParameterSource("sourceDocumentId", sourceDocumentId);

        return jdbcTemplate.update(sql, params);
    }
}
