package com.distributedplatform.llmfundamentals.embedding;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class OllamaEmbeddingService {

    private final RestClient restClient;

    @Autowired
    public OllamaEmbeddingService(@Value("${ollama.base-url}") String baseUrl) {
        this(RestClient.builder()
                .baseUrl(baseUrl)
                .build());
    }

    public OllamaEmbeddingService(RestClient restClient) {
        this.restClient = restClient;
    }

    public double[] embed(String model, String input) {
        OllamaEmbedRequest requestPayload = new OllamaEmbedRequest(model, input);

        OllamaEmbedResponse response = restClient.post()
                .uri("/api/embed")
                .body(requestPayload)
                .retrieve()
                .body(OllamaEmbedResponse.class);

        if (response == null || response.getEmbeddings() == null || response.getEmbeddings().isEmpty()) {
            throw new IllegalStateException("Ollama returned empty or null embeddings for input");
        }

        return response.getEmbeddings().get(0);
    }
}
