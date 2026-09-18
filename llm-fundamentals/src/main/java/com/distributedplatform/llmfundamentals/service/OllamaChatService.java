package com.distributedplatform.llmfundamentals.service;

import com.distributedplatform.llmfundamentals.dto.ollama.Message;
import com.distributedplatform.llmfundamentals.dto.ollama.OllamaRequest;
import com.distributedplatform.llmfundamentals.dto.ollama.OllamaResponse;
import com.distributedplatform.llmfundamentals.dto.ollama.Tool;
import com.distributedplatform.llmfundamentals.dto.ollama.ToolCall;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class OllamaChatService {

    private final RestClient restClient;

    public OllamaChatService(@Value("${ollama.base-url}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public OllamaResponse chat(String model, List<Message> messages) {
        OllamaRequest requestPayload = new OllamaRequest(model, messages, false, null, null);

        return restClient.post()
                .uri("/api/chat")
                .body(requestPayload)
                .retrieve()
                .body(OllamaResponse.class);
    }

    public OllamaResponse chatWithTools(String model, List<Message> messages, List<Tool> tools) {
        OllamaRequest requestPayload = new OllamaRequest(model, messages, false, null, tools);

        return restClient.post()
                .uri("/api/chat")
                .body(requestPayload)
                .retrieve()
                .body(OllamaResponse.class);
    }

    public String executeFakeWeather(String city) {
        return String.format("{\"city\": \"%s\", \"condition\": \"Sunny\", \"temperature_c\": 22}", city);
    }

    public OllamaResponse chatWithWeatherTool(String model, List<Message> messages) {
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

        // Turn 1: Call Ollama with tool definition and user prompt
        OllamaResponse turn1Response = chatWithTools(model, messages, List.of(weatherTool));
        if (turn1Response == null || turn1Response.getMessage() == null) {
            return turn1Response;
        }

        List<ToolCall> toolCalls = turn1Response.getMessage().getToolCalls();
        if (toolCalls == null || toolCalls.isEmpty()) {
            return turn1Response;
        }

        // Prepare conversation history with assistant tool call decision
        List<Message> conversation = new ArrayList<>(messages);
        conversation.add(turn1Response.getMessage());

        // Execute each requested tool and append a corresponding tool result message,
        // so every tool call the model made has an answer waiting for it in history.
        for (ToolCall toolCall : toolCalls) {
            conversation.add(executeToolCall(toolCall));
        }

        // Turn 2: Send entire history back via standard chat(...) to get final natural language answer
        return chat(model, conversation);
    }

    /**
     * Dispatches a single tool call to its implementation and returns the "tool" role
     * message to append to history. An unrecognized tool name returns an explicit
     * error result instead of silently producing no message at all — a dangling,
     * unanswered tool call would otherwise leave the model waiting on a response that
     * never comes.
     */
    Message executeToolCall(ToolCall toolCall) {
        if (toolCall.getFunction() == null) {
            return new Message("tool", "{\"error\": \"Malformed tool call: missing function\"}");
        }

        String name = toolCall.getFunction().getName();
        if ("get_weather".equals(name)) {
            String city = "unknown";
            Map<String, Object> arguments = toolCall.getFunction().getArguments();
            if (arguments != null && arguments.containsKey("city")) {
                city = String.valueOf(arguments.get("city"));
            }
            return new Message("tool", executeFakeWeather(city));
        }

        return new Message("tool", String.format("{\"error\": \"Unknown tool: %s\"}", name));
    }

    public <T> T chatStructured(String model, List<Message> messages, Map<String, Object> schema, Class<T> responseType) {
        // build the request with the schema in its `format` field, same POST as before
        OllamaRequest requestPayload = new OllamaRequest(model, messages, false, schema, null);

        OllamaResponse response = restClient.post()
                .uri("/api/chat")
                .body(requestPayload)
                .retrieve()
                .body(OllamaResponse.class);
        // then: objectMapper.readValue(response.getMessage().getContent(), responseType)
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(response.getMessage().getContent(), responseType);
    }
}
