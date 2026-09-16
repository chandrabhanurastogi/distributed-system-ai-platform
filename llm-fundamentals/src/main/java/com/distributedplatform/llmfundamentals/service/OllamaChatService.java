package com.distributedplatform.llmfundamentals.service;

import com.distributedplatform.llmfundamentals.dto.Message;
import com.distributedplatform.llmfundamentals.dto.OllamaRequest;
import com.distributedplatform.llmfundamentals.dto.OllamaResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

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
        OllamaRequest requestPayload = new OllamaRequest(model, messages, false, null);

        return restClient.post()
                .uri("/api/chat")
                .body(requestPayload)
                .retrieve()
                .body(OllamaResponse.class);
    }

    public <T> T chatStructured(String model, List<Message> messages, Map<String, Object> schema, Class<T> responseType) {
        // build the request with the schema in its `format` field, same POST as before
        OllamaRequest requestPayload = new OllamaRequest(model, messages, false, schema);

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
