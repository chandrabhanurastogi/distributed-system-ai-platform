package com.distributedplatform.llmfundamentals.controller;

import com.distributedplatform.llmfundamentals.dto.ExtractedPerson;
import com.distributedplatform.llmfundamentals.dto.ollama.Message;
import com.distributedplatform.llmfundamentals.dto.ollama.OllamaResponse;
import com.distributedplatform.llmfundamentals.service.OllamaChatService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class ChatController {

    private final OllamaChatService ollamaChatService;

    public ChatController(OllamaChatService ollamaChatService) {
        this.ollamaChatService = ollamaChatService;
    }

    @PostMapping("/chat")
    public String handleChat(@RequestBody List<Message> conversationHistory) {
        OllamaResponse response = ollamaChatService.chat("llama3.2", conversationHistory);

        if (response != null && response.getMessage() != null) {
            return response.getMessage().getContent();
        }

        return "Error: No response received from the model.";
    }

    @PostMapping("/chat/weather")
    public String chatWithWeather(@RequestBody String prompt) {
        List<Message> messages = List.of(new Message("user", prompt));
        OllamaResponse response = ollamaChatService.chatWithWeatherTool("llama3.2", messages);

        if (response != null && response.getMessage() != null) {
            return response.getMessage().getContent();
        }

        return "Error: No response received from the model.";
    }

    @PostMapping("/extract-person")
    public ExtractedPerson extractPerson(@RequestBody String text) {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "name", Map.of("type", "string"),
                        "age", Map.of("type", "integer")
                ),
                "required", List.of("name", "age")
        );

        List<Message> messages = List.of(new Message("user", "Extract the name and age from: " + text));

        return ollamaChatService.chatStructured("llama3.2", messages, schema, ExtractedPerson.class);
    }
}
