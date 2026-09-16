package com.distributedplatform.llmfundamentals.service;

import com.distributedplatform.llmfundamentals.dto.Message;
import com.distributedplatform.llmfundamentals.dto.ToolCall;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests for OllamaChatService.executeToolCall's dispatch logic — no Spring
 * context, no real Ollama call, hand-built ToolCall fixtures. These cover branches
 * llama3.2 never naturally exercises against the one real tool this service knows
 * about (only "get_weather" is ever actually requested in practice), which is exactly
 * why they can't be proven via the real-Ollama integration tests alone.
 */
class OllamaChatServiceToolDispatchTest {

    private final OllamaChatService service = new OllamaChatService("http://unused");

    @Test
    void executeToolCall_knownTool_returnsWeatherResult() {
        ToolCall.Function function = new ToolCall.Function("get_weather", Map.of("city", "Paris"));
        ToolCall toolCall = new ToolCall("call_1", function);

        Message result = service.executeToolCall(toolCall);

        assertEquals("tool", result.getRole());
        assertTrue(result.getContent().contains("Paris"));
        assertTrue(result.getContent().contains("Sunny"));
    }

    @Test
    void executeToolCall_unknownTool_returnsExplicitErrorNotSilence() {
        ToolCall.Function function = new ToolCall.Function("get_stock_price", Map.of("symbol", "ACME"));
        ToolCall toolCall = new ToolCall("call_2", function);

        Message result = service.executeToolCall(toolCall);

        assertEquals("tool", result.getRole());
        assertTrue(result.getContent().contains("Unknown tool"));
        assertTrue(result.getContent().contains("get_stock_price"));
    }

    @Test
    void executeToolCall_malformedCallWithNoFunction_returnsExplicitErrorNotException() {
        ToolCall toolCall = new ToolCall("call_3", null);

        Message result = service.executeToolCall(toolCall);

        assertEquals("tool", result.getRole());
        assertTrue(result.getContent().contains("Malformed tool call"));
    }
}
