package com.distributedplatform.disputeservice.embedding;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OllamaEmbeddingServiceTest {

    private static final String EMBED_MODEL = "nomic-embed-text";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @ParameterizedTest
    @NullAndEmptySource
    void embed_whenEmbeddingsListIsNullOrEmpty_throwsIllegalStateException(List<double[]> embeddings) throws Exception {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://mock-ollama");
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
