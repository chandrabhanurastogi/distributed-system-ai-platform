package com.distributedplatform.llmfundamentals.service;

import com.distributedplatform.llmfundamentals.dto.Message;
import com.distributedplatform.llmfundamentals.dto.OllamaResponse;
import com.distributedplatform.llmfundamentals.dto.Tool;
import com.distributedplatform.llmfundamentals.dto.ToolCall;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class OllamaChatServiceTest {

    private static final Logger log = LoggerFactory.getLogger(OllamaChatServiceTest.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private OllamaChatService ollamaChatService;

    @Test
    void chat_withRealOllama_returnsResponseWithRealTokenCounts() throws Exception {
        // Arrange
        List<Message> messages = List.of(
                new Message("user", "Explain recursion in one short sentence.")
        );

        // Act
        OllamaResponse response = ollamaChatService.chat("llama3.2", messages);

        // Log Ollama Response
        log.info("Ollama Chat Response:\n{}", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(response));

        // Assert
        assertNotNull(response, "OllamaResponse should not be null");
        assertNotNull(response.getMessage(), "Response message should not be null");
        assertNotNull(response.getMessage().getContent(), "Response message content should not be null");
        assertFalse(response.getMessage().getContent().isBlank(), "Response content should not be blank");
        assertTrue(response.isDone(), "Response should be marked as done");

        // Assert on genuine token evaluation metrics from real Ollama invocation
        assertTrue(response.getPromptEvalCount() > 0,
                "promptEvalCount should be > 0, got: " + response.getPromptEvalCount());
        assertTrue(response.getEvalCount() > 0,
                "evalCount should be > 0, got: " + response.getEvalCount());
    }

    @Test
    void chatWithTools_withTriggeringPrompt_returnsToolCall() throws Exception {
        // Arrange
        Tool weatherTool = new Tool(
                "get_weather",
                "Get the current weather for a given city",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "city", Map.of("type", "string", "description", "The city name")
                        ),
                        "required", List.of("city")
                )
        );

        List<Message> messages = List.of(
                new Message("user", "What is the weather in Paris right now?")
        );

        // Act
        OllamaResponse response = ollamaChatService.chatWithTools("llama3.2", messages, List.of(weatherTool));

        // Log Ollama Response
        log.info("Ollama Chat With Tools Response:\n{}", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(response));

        // Assert
        assertNotNull(response, "OllamaResponse should not be null");
        assertNotNull(response.getMessage(), "Response message should not be null");
        List<ToolCall> toolCalls = response.getMessage().getToolCalls();
        assertNotNull(toolCalls, "toolCalls should not be null when prompt triggers a tool call");
        assertFalse(toolCalls.isEmpty(), "toolCalls should contain at least one tool call");

        ToolCall firstCall = toolCalls.get(0);
        assertNotNull(firstCall.getFunction(), "ToolCall function should not be null");
        assertEquals("get_weather", firstCall.getFunction().getName());
        assertNotNull(firstCall.getFunction().getArguments(), "Arguments should not be null");
        assertTrue(firstCall.getFunction().getArguments().containsKey("city"), "Arguments should contain 'city' key");
        assertEquals("Paris", firstCall.getFunction().getArguments().get("city"));
    }

    @Test
    void chatWithWeatherTool_executesFullRoundTrip_incorporatesFakeToolResult() throws Exception {
        // Arrange
        List<Message> messages = List.of(
                new Message("user", "What is the weather in Paris right now?")
        );

        // Act: Execute round-trip: (1) model triggers tool -> (2) Java executes fake tool -> (3) model produces final answer
        OllamaResponse response = ollamaChatService.chatWithWeatherTool("llama3.2", messages);

        // Log Ollama Final Response
        log.info("Ollama Final Natural Language Response:\n{}", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(response));

        // Assert
        assertNotNull(response, "Response should not be null");
        assertNotNull(response.getMessage(), "Message should not be null");
        String finalAnswer = response.getMessage().getContent();
        assertNotNull(finalAnswer, "Final answer content should not be null");
        assertFalse(finalAnswer.isBlank(), "Final answer content should not be blank");

        String lowerCaseAnswer = finalAnswer.toLowerCase(Locale.ROOT);
        // Verify that the final natural language answer incorporated the fake weather tool's output ("Sunny", 22°C)
        assertTrue(lowerCaseAnswer.contains("sunny") || lowerCaseAnswer.contains("22"),
                "Final answer should mention the fake weather result ('sunny' or '22'). Got: " + finalAnswer);
    }
}
