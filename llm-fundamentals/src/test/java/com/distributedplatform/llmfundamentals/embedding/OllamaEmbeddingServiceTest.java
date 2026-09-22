package com.distributedplatform.llmfundamentals.embedding;

import com.distributedplatform.llmfundamentals.vector.VectorMath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@SpringBootTest
class OllamaEmbeddingServiceTest {

    private static final Logger log = LoggerFactory.getLogger(OllamaEmbeddingServiceTest.class);
    private static final String EMBED_MODEL = "nomic-embed-text";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private OllamaEmbeddingService ollamaEmbeddingService;

    @Autowired
    private RestClient.Builder restClientBuilder;

    @Test
    void embed_withRealOllama_returnsExpected768DimensionVectorAndComposesWithVectorMath() {
        // Arrange
        String text = "Distributed systems require reliable communication protocols.";

        // Act - single call to embed the text
        double[] vector = ollamaEmbeddingService.embed(EMBED_MODEL, text);

        log.info("Received vector with dimensionality: {}", vector.length);
        log.info("First 5 vector components: [{}, {}, {}, {}, {}]",
                vector[0], vector[1], vector[2], vector[3], vector[4]);

        // Assert - verify dimensionality for nomic-embed-text
        assertThat(vector).isNotNull();
        assertThat(vector.length).isEqualTo(768);

        // Verify composition with VectorMath: a single vector with itself evaluates to 1.0
        // without relying on multi-call GPU non-deterministic reproducibility
        double selfSimilarity = VectorMath.cosineSimilarity(vector, vector);
        log.info("Cosine similarity of vector with itself: {}", selfSimilarity);
        assertThat(selfSimilarity).isCloseTo(1.0, within(1e-9));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void embed_whenEmbeddingsListIsNullOrEmpty_throwsIllegalStateException(List<double[]> embeddings) throws Exception {
        RestClient.Builder builder = restClientBuilder.clone().baseUrl("http://mock-ollama");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OllamaEmbeddingService service = new OllamaEmbeddingService(builder.build());

        OllamaEmbedResponse response = new OllamaEmbedResponse(EMBED_MODEL, embeddings, 100L, 50L, 10);

        server.expect(requestTo("http://mock-ollama/api/embed"))
                .andExpect(jsonPath("$.model").value(EMBED_MODEL))
                .andExpect(jsonPath("$.input").value("test"))
                .andRespond(withSuccess(objectMapper.writeValueAsString(response), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.embed(EMBED_MODEL, "test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Ollama returned empty or null embeddings for input");

        server.verify();
    }
}
