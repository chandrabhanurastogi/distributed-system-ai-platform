package com.distributedplatform.llmfundamentals.service;

import com.distributedplatform.llmfundamentals.dto.ChatMessage;
import com.distributedplatform.llmfundamentals.dto.LlmClient;
import com.distributedplatform.llmfundamentals.dto.LlmResponse;
import com.distributedplatform.llmfundamentals.dto.gemini.GeminiRequest;
import com.distributedplatform.llmfundamentals.dto.gemini.GeminiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Adapter implementing {@link LlmClient} for Gemini.
 *
 * <p><strong>Design Decision on Multi-turn History Serialization:</strong></p>
 * <p>We selected <strong>Option A (Role-Prefixed Dialogue Transcript with Assistant Priming)</strong>
 * over Option B (Markdown Delimiters):</p>
 * <ul>
 *   <li><strong>Token Efficiency & Free-Tier Cost Conservation:</strong> In free or quota-constrained
 *       environments (such as the Google Gemini free tier), markdown section headers ({@code ### User:}, {@code ### Assistant:})
 *       add extra structural token overhead on every turn. Over multi-turn conversations, this accumulates
 *       unnecessary prompt token consumption. Option A minimizes per-turn token waste.</li>
 *   <li><strong>Natural Prompt Priming:</strong> Ending the serialized transcript with {@code Assistant:} primes the model
 *       to complete only its assigned turn in the conversational dialogue without hallucinating further user turns
 *       or echoing markers.</li>
 *   <li><strong>Trade-off:</strong> While Option B provides slightly stronger boundary separation for complex multi-line code blocks
 *       when cost is not a concern, Option A offers the optimal balance of conversational coherence and minimal token footprint.</li>
 * </ul>
 */
@Service
@ConditionalOnProperty(name = "llm.provider", havingValue = "gemini")
public class GeminiLlmClient implements LlmClient {

    private final RestClient restClient;
    private final String model;
    private final String apiKey;

    @Autowired
    public GeminiLlmClient(
            @Value("${gemini.base-url:https://generativelanguage.googleapis.com/v1beta}") String baseUrl,
            @Value("${gemini.api-key:}") String apiKey,
            @Value("${gemini.model:gemini-3.6-flash}") String model) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.apiKey = apiKey;
        this.model = model;
    }

    public GeminiLlmClient(RestClient restClient, String model, String apiKey) {
        this.restClient = restClient;
        this.model = model;
        this.apiKey = apiKey;
    }

    @Override
    public LlmResponse chat(List<ChatMessage> messages) {
        String input = serializeMessages(messages);
        GeminiRequest request = new GeminiRequest(model, input);

        RestClient.RequestHeadersSpec<?> spec = restClient.post()
                .uri(uriBuilder -> uriBuilder.path("/interactions").build())
                .body(request);

        if (apiKey != null && !apiKey.isBlank()) {
            spec = spec.header("x-goog-api-key", apiKey);
        }

        GeminiResponse response = spec.retrieve()
                .body(GeminiResponse.class);

        return mapToLlmResponse(response);
    }

    /**
     * Serializes a list of {@link ChatMessage} objects into a single flat prompt string.
     * Uses Option A: role prefixes with trailing "Assistant:" priming anchor for multi-turn conversations.
     */
    public String serializeMessages(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }

        if (messages.size() == 1 && "user".equalsIgnoreCase(messages.get(0).role())) {
            return messages.get(0).content();
        }

        StringBuilder sb = new StringBuilder();
        for (ChatMessage msg : messages) {
            String role = msg.role() == null ? "User" : capitalize(msg.role());
            sb.append(role).append(": ").append(msg.content() == null ? "" : msg.content()).append("\n\n");
        }
        sb.append("Assistant:");
        return sb.toString();
    }

    private LlmResponse mapToLlmResponse(GeminiResponse response) {
        if (response == null) {
            return new LlmResponse("", 0, 0);
        }

        String content = "";
        if (response.getSteps() != null && !response.getSteps().isEmpty()) {
            content = response.getSteps().stream()
                    .filter(step -> step.getContent() != null)
                    .flatMap(step -> step.getContent().stream())
                    .filter(item -> item != null && item.getText() != null)
                    .map(GeminiResponse.ContentItem::getText)
                    .collect(Collectors.joining("\n"));
        }

        int inputTokens = 0;
        int outputTokens = 0;
        if (response.getUsage() != null) {
            inputTokens = response.getUsage().getTotalInputTokens();
            outputTokens = response.getUsage().getTotalOutputTokens();
        }

        return new LlmResponse(content, inputTokens, outputTokens);
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1).toLowerCase();
    }
}
