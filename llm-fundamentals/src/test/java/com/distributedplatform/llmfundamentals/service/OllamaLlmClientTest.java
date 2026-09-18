package com.distributedplatform.llmfundamentals.service;

import com.distributedplatform.llmfundamentals.dto.ChatMessage;
import com.distributedplatform.llmfundamentals.dto.LlmClient;
import com.distributedplatform.llmfundamentals.dto.LlmResponse;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class OllamaLlmClientTest {

    private static final Logger log = LoggerFactory.getLogger(OllamaLlmClientTest.class);

    @Autowired
    private LlmClient llmClient;

    @Test
    void chat_throughLlmClient_returnsFaithfulResponseWithRealTokenCounts() {
        // Arrange
        List<ChatMessage> messages = List.of(
                new ChatMessage("user", "Explain recursion in one short sentence.")
        );

        // Act - invoke Ollama through the generic LlmClient interface
        LlmResponse response = llmClient.chat(messages);

        // Log response
        log.info("LlmResponse received: content='{}', inputTokens={}, outputTokens={}",
                response.content(), response.inputTokens(), response.outputTokens());

        // Assert
        assertNotNull(response, "LlmResponse should not be null");
        assertNotNull(response.content(), "Content should not be null");
        assertFalse(response.content().isBlank(), "Content should not be blank");

        // Verify token count translation through the adapter boundary
        assertTrue(response.inputTokens() > 0,
                "inputTokens should be > 0, got: " + response.inputTokens());
        assertTrue(response.outputTokens() > 0,
                "outputTokens should be > 0, got: " + response.outputTokens());
    }
}
