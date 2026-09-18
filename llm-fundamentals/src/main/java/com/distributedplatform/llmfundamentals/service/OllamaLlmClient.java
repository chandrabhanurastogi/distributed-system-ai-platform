package com.distributedplatform.llmfundamentals.service;

import com.distributedplatform.llmfundamentals.dto.ChatMessage;
import com.distributedplatform.llmfundamentals.dto.LlmClient;
import com.distributedplatform.llmfundamentals.dto.LlmResponse;
import com.distributedplatform.llmfundamentals.dto.ollama.Message;
import com.distributedplatform.llmfundamentals.dto.ollama.OllamaResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConditionalOnProperty(name = "llm.provider", havingValue = "ollama", matchIfMissing = true)
public class OllamaLlmClient implements LlmClient {

    private final OllamaChatService ollamaChatService;
    private final String model;

    @Autowired
    public OllamaLlmClient(OllamaChatService ollamaChatService,
                           @Value("${ollama.model:llama3.2}") String model) {
        this.ollamaChatService = ollamaChatService;
        this.model = model;
    }

    public OllamaLlmClient(OllamaChatService ollamaChatService) {
        this(ollamaChatService, "llama3.2");
    }

    @Override
    public LlmResponse chat(List<ChatMessage> messages) {
        List<Message> internalMessages = messages.stream()
                .map(m -> new Message(m.role(), m.content()))
                .toList();

        OllamaResponse response = ollamaChatService.chat(model, internalMessages);

        String content = (response != null && response.getMessage() != null)
                ? response.getMessage().getContent()
                : "";

        int inputTokens = response != null ? (int) response.getPromptEvalCount() : 0;
        int outputTokens = response != null ? (int) response.getEvalCount() : 0;

        return new LlmResponse(content, inputTokens, outputTokens);
    }
}
