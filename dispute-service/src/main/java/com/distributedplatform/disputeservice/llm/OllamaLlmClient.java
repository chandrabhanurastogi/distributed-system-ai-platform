package com.distributedplatform.disputeservice.llm;

import com.distributedplatform.common.llm.ChatMessage;
import com.distributedplatform.common.llm.LlmClient;
import com.distributedplatform.common.llm.LlmResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Ollama-based, single-turn only. Deliberately not a general-purpose LlmClient: no
 * tool-calling, no structured output, no multi-turn history handling - classification
 * (claim text + retrieved context in, a classification out) never needs any of that.
 * Ollama's real /api/chat endpoint takes a native messages array, so - unlike
 * GeminiLlmClient - there is no message-flattening step here at all, and therefore no
 * exposure to the tracked flattening bug that lives entirely in that class.
 * <p>
 * ChatMessage (common.llm, shared with llm-fundamentals) is mapped to and from a local
 * OllamaMessage wire type at this boundary, mirroring llm-fundamentals's own
 * OllamaLlmClient exactly - even though the two types happen to have identical fields
 * today, keeping them separate means a change to the shared interface DTO for reasons
 * unrelated to this client can never silently alter what actually gets sent over the
 * wire here.
 */
@Service
public class OllamaLlmClient implements LlmClient {

    // Classification wants a consistent decision for the same input, not creative
    // variety - temperature 0 makes Ollama's sampling effectively deterministic.
    private static final double TEMPERATURE = 0.0;

    private final RestClient restClient;
    private final String model;

    @Autowired
    public OllamaLlmClient(@Value("${ollama.base-url}") String baseUrl,
                            @Value("${ollama.model:llama3.2}") String model) {
        this(RestClient.builder().baseUrl(baseUrl).build(), model);
    }

    public OllamaLlmClient(RestClient restClient, String model) {
        this.restClient = restClient;
        this.model = model;
    }

    @Override
    public LlmResponse chat(List<ChatMessage> messages) {
        List<OllamaMessage> wireMessages = messages.stream()
                .map(m -> new OllamaMessage(m.role(), m.content()))
                .toList();

        OllamaChatRequest requestPayload = new OllamaChatRequest(
                model, wireMessages, false, new OllamaChatRequest.Options(TEMPERATURE));

        OllamaChatResponse response = restClient.post()
                .uri("/api/chat")
                .body(requestPayload)
                .retrieve()
                .body(OllamaChatResponse.class);

        String content = (response != null && response.getMessage() != null)
                ? response.getMessage().getContent()
                : "";
        int inputTokens = response != null ? (int) response.getPromptEvalCount() : 0;
        int outputTokens = response != null ? (int) response.getEvalCount() : 0;

        return new LlmResponse(content, inputTokens, outputTokens);
    }
}
