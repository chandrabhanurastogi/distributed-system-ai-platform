package com.distributedplatform.disputeservice;

import com.distributedplatform.disputeservice.embedding.OllamaEmbeddingService;
import com.distributedplatform.disputeservice.embedding.VectorLiterals;
import com.distributedplatform.disputeservice.service.DisputeDocumentIngestionService;
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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Not a mocked/hand-crafted test - the real pipeline, end-to-end: a real policy
 * document goes in through DisputeDocumentIngestionService (chunk -> embed -> store,
 * against real Ollama and a real Testcontainers Postgres with pgvector), then a
 * realistic question is embedded and run through the same "ORDER BY embedding <=> ?"
 * mechanics already proven in Milestone 8.1, checking that the top result is actually
 * the relevant passage - the dispute-service equivalent of Milestone 7.2's
 * BruteForceRetrievalIntegrationTest.
 */
@SpringBootTest
@Testcontainers
@Transactional
class DisputeDocumentIngestionIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(DisputeDocumentIngestionIntegrationTest.class);
    private static final String EMBEDDING_MODEL = "nomic-embed-text";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private DisputeDocumentIngestionService ingestionService;

    @Autowired
    private OllamaEmbeddingService embeddingService;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Test
    void ingest_withRealPolicyDocument_retrievesTheRelevantChunkForARealisticQuestion() {
        String document = loadResource("/documents/cardholder-dispute-policy.txt");

        long start = System.nanoTime();
        List<Long> generatedIds = ingestionService.ingest(document);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        log.info("Ingested real policy document into {} chunks in {} ms ({} ms/chunk)",
                generatedIds.size(), elapsedMs, elapsedMs / generatedIds.size());

        assertThat(generatedIds).isNotEmpty();

        // The document's filing-deadline paragraph is the only one that mentions
        // "deadline" - a question specifically about filing deadlines should retrieve
        // it above the fraud/reason-code and non-fraud/reason-code paragraphs.
        String question = "How many days do I have to file a chargeback dispute?";
        double[] queryVector = embeddingService.embed(EMBEDDING_MODEL, question);

        String sql = """
                SELECT text, embedding <=> CAST(:query AS vector) AS distance
                FROM dispute_documents
                ORDER BY distance ASC
                LIMIT 1
                """;
        MapSqlParameterSource params = new MapSqlParameterSource("query", VectorLiterals.toVectorLiteral(queryVector));

        List<String> topResult = jdbcTemplate.query(sql, params, (rs, rowNum) -> rs.getString("text"));

        log.info("Question: \"{}\"\nTop retrieved chunk: \"{}\"", question, topResult.get(0));

        assertThat(topResult).hasSize(1);
        assertThat(topResult.get(0)).containsIgnoringCase("deadline");
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
