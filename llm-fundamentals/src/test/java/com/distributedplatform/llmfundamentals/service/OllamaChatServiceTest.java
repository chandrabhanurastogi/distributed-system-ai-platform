package com.distributedplatform.llmfundamentals.service;

import com.distributedplatform.llmfundamentals.dto.Message;
import com.distributedplatform.llmfundamentals.dto.OllamaResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class OllamaChatServiceTest {

    @Autowired
    private OllamaChatService ollamaChatService;

    @Test
    void chat_withRealOllama_returnsResponseWithRealTokenCounts() {
        // Arrange
        List<Message> messages = List.of(
                new Message("user", "Explain recursion in one short sentence.")
        );

        // Act
        OllamaResponse response = ollamaChatService.chat("llama3.2", messages);

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
}
