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
                INSERT INTO dispute_documents (text, embedding)
                VALUES (:text, CAST(:embedding AS vector))
                RETURNING id
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("text", document.text())
                .addValue("embedding", VectorLiterals.toVectorLiteral(document.embedding()));

        return jdbcTemplate.queryForObject(sql, params, Long.class);
    }
}
