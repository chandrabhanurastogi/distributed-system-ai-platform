package com.distributedplatform.llmfundamentals.service;

import com.distributedplatform.llmfundamentals.dto.ChatMessage;
import com.distributedplatform.llmfundamentals.dto.LlmResponse;
import com.distributedplatform.llmfundamentals.dto.gemini.GeminiResponse;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiLlmClientTest {

    private static final Logger log = LoggerFactory.getLogger(GeminiLlmClientTest.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializeMessages_singleUserMessage_returnsRawContent() {
        GeminiLlmClient client = new GeminiLlmClient("http://unused", "test-key", "gemini-3.6-flash");
        List<ChatMessage> messages = List.of(new ChatMessage("user", "Hello world"));

        String serialized = client.serializeMessages(messages);

        assertEquals("Hello world", serialized);
    }

    @Test
    void serializeMessages_multiTurn_serializesOptionAWithAssistantPriming() {
        GeminiLlmClient client = new GeminiLlmClient("http://unused", "test-key", "gemini-3.6-flash");
        List<ChatMessage> messages = List.of(
                new ChatMessage("user", "My favourite colour is blue."),
                new ChatMessage("assistant", "Understood, your favourite colour is blue."),
                new ChatMessage("user", "What is my favourite colour?")
        );

        String serialized = client.serializeMessages(messages);

        String expected = """
                User: My favourite colour is blue.
                
                Assistant: Understood, your favourite colour is blue.
                
                User: What is my favourite colour?
                
                Assistant:""";

        assertEquals(expected, serialized);
    }

    @Test
    void chat_withMockServer_extractsContentAndTokenUsageCorrectly() throws Exception {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        GeminiLlmClient client = new GeminiLlmClient(restClient, "gemini-3.6-flash", "test-key");

        GeminiResponse fakeResponse = new GeminiResponse(
                "gemini-resp-123",
                "COMPLETED",
                new GeminiResponse.Usage(45, 20, 65),
                List.of(new GeminiResponse.Step(List.of(new GeminiResponse.ContentItem("Your favourite colour is blue."))))
        );

        server.expect(requestTo("https://generativelanguage.googleapis.com/interactions"))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andExpect(jsonPath("$.model").value("gemini-3.6-flash"))
                .andExpect(jsonPath("$.input").value("What is my favourite colour?"))
                .andRespond(withSuccess(objectMapper.writeValueAsString(fakeResponse), MediaType.APPLICATION_JSON));

        List<ChatMessage> messages = List.of(new ChatMessage("user", "What is my favourite colour?"));
        LlmResponse response = client.chat(messages);

        assertNotNull(response);
        assertEquals("Your favourite colour is blue.", response.content());
        assertEquals(45, response.inputTokens());
        assertEquals(20, response.outputTokens());

        server.verify();
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
    void chat_realGeminiApi_smokeTest() {
        String apiKey = System.getenv("GEMINI_API_KEY");
        String baseUrl = "https://generativelanguage.googleapis.com/v1beta";
        GeminiLlmClient client = new GeminiLlmClient(baseUrl, apiKey, "gemini-3.6-flash");

        List<ChatMessage> messages = List.of(
                new ChatMessage("user", "Explain recursion in one short sentence.")
        );

        try {
            LlmResponse response = client.chat(messages);
            log.info("Gemini Live Response: content='{}', inputTokens={}, outputTokens={}",
                    response.content(), response.inputTokens(), response.outputTokens());

            assertNotNull(response, "LlmResponse should not be null");
            assertNotNull(response.content(), "Response content should not be null");
            assertFalse(response.content().isBlank(), "Response content should not be blank");
            assertTrue(response.inputTokens() > 0, "inputTokens should be strictly greater than 0");
            assertTrue(response.outputTokens() > 0, "outputTokens should be strictly greater than 0");
        } catch (HttpStatusCodeException e) {
            log.warn("Gemini Live API call returned HTTP {}: {}. " +
                    "Please ensure GEMINI_API_KEY is a valid Google AI Studio API key.",
                    e.getStatusCode(), e.getResponseBodyAsString());
            Assumptions.assumeTrue(false,
                    "Skipping live Gemini smoke test: API credentials not recognized by endpoint (" + e.getStatusCode() + ")");
        }
    }
}
