package com.distributedplatform.disputeservice;

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

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Milestone 8.1's first slice: no repository class yet (ADR-0004 — one earns its place
 * once real domain code needs it), no REST endpoint, no real corpus. This proves the
 * SQL-level mechanics work — real pgvector extension, real vector(768) column, real
 * "ORDER BY embedding <=> ?" query — using hand-computed vectors with a mathematically
 * certain expected answer, the SQL equivalent of Milestone 7.1's hand-crafted-vector
 * tests. Vectors are 768-dimensional (matching nomic-embed-text's real measured
 * dimensionality from Phase 7) but mostly zero, with a single 1.0/-1.0 placed on one of
 * two axes — the expected cosine distance for each is computable by hand, not just
 * plausible-looking.
 */
@SpringBootTest
@Testcontainers
@Transactional
class DisputeDocumentSimilarityIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(DisputeDocumentSimilarityIntegrationTest.class);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Test
    void orderByCosineDistance_withHandComputedVectors_returnsCorrectNearestNeighborOrder() {
        // Query points along axis 0
        double[] queryVector = unitAxisVector(0, 1.0);

        // doc-parallel: same direction as query -> cosine distance = 0 (identical)
        insertDocument("doc-parallel", unitAxisVector(0, 2.0));
        // doc-orthogonal: perpendicular to query (axis 1) -> cosine distance = 1
        insertDocument("doc-orthogonal", unitAxisVector(1, 1.0));
        // doc-opposite: opposite direction (axis 0, negative) -> cosine distance = 2 (max)
        insertDocument("doc-opposite", unitAxisVector(0, -1.0));

        String sql = """
                SELECT text, embedding <=> CAST(:query AS vector) AS distance
                FROM dispute_documents
                ORDER BY distance ASC
                """;
        MapSqlParameterSource params = new MapSqlParameterSource("query", toVectorLiteral(queryVector));

        List<String> rankedByNearest = jdbcTemplate.query(sql, params,
                (rs, rowNum) -> rs.getString("text"));

        assertEquals(List.of("doc-parallel", "doc-orthogonal", "doc-opposite"), rankedByNearest);
    }

    @Test
    void explainAnalyze_withNoIndexOnEmbedding_showsSeqScanFeedingASortNode() {
        // Not a mocked/assumed plan - the real EXPLAIN ANALYZE output from the same
        // Testcontainers instance and the same 3 hand-computed rows, verifying the
        // mechanical trace worked through in Milestone 8.1's interview: no index means
        // Postgres must Seq Scan every row (computing <=> as it goes), then feed the
        // full buffered result into a separate Sort node - it cannot emit anything in
        // order until every row's distance has been computed.
        double[] queryVector = unitAxisVector(0, 1.0);
        insertDocument("doc-parallel", unitAxisVector(0, 2.0));
        insertDocument("doc-orthogonal", unitAxisVector(1, 1.0));
        insertDocument("doc-opposite", unitAxisVector(0, -1.0));

        String explainSql = """
                EXPLAIN ANALYZE
                SELECT text, embedding <=> CAST(:query AS vector) AS distance
                FROM dispute_documents
                ORDER BY distance ASC
                """;
        MapSqlParameterSource params = new MapSqlParameterSource("query", toVectorLiteral(queryVector));

        List<String> planLines = jdbcTemplate.query(explainSql, params,
                (rs, rowNum) -> rs.getString("QUERY PLAN"));
        String fullPlan = String.join("\n", planLines);

        log.info("Real query plan, no index on dispute_documents.embedding:\n{}", fullPlan);

        assertThat(fullPlan).contains("Seq Scan on dispute_documents");
        assertThat(fullPlan).contains("Sort");
    }

    private void insertDocument(String text, double[] embedding) {
        String sql = """
                INSERT INTO dispute_documents (text, embedding)
                VALUES (:text, CAST(:embedding AS vector))
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("text", text)
                .addValue("embedding", toVectorLiteral(embedding));

        jdbcTemplate.update(sql, params);
    }

    private double[] unitAxisVector(int axis, double value) {
        double[] vector = new double[768];
        vector[axis] = value;
        return vector;
    }

    private String toVectorLiteral(double[] vector) {
        return "[" + IntStream.range(0, vector.length)
                .mapToObj(i -> Double.toString(vector[i]))
                .collect(Collectors.joining(",")) + "]";
    }
}
