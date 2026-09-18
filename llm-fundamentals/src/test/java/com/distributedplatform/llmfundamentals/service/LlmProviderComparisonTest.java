package com.distributedplatform.llmfundamentals.service;

import com.distributedplatform.llmfundamentals.dto.ChatMessage;
import com.distributedplatform.llmfundamentals.dto.LlmClient;
import com.distributedplatform.llmfundamentals.dto.LlmResponse;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.HttpStatusCodeException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Not a correctness test — a real, honest measurement (Rule 9: no fabricated numbers).
 * Sends the identical prompt through both real LlmClient implementations and logs
 * latency and token counts side by side. Requires GEMINI_API_KEY to be set in the
 * environment actually running this test (not visible from every environment — see
 * ROADMAP.md Milestone 6.3 for why that distinction matters).
 */
class LlmProviderComparisonTest {

    private static final Logger log = LoggerFactory.getLogger(LlmProviderComparisonTest.class);
    private static final String PROMPT = "Explain recursion in one short sentence.";

    @Test
    @EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
    void compareOllamaAndGemini_sameProm_logsRealLatencyAndTokenCounts() {
        List<ChatMessage> messages = List.of(new ChatMessage("user", PROMPT));

        LlmClient ollama = new OllamaLlmClient(new OllamaChatService("http://localhost:11434"));
        Result ollamaResult = timedCall("Ollama (llama3.2, local, free)", ollama, messages);

        String geminiApiKey = System.getenv("GEMINI_API_KEY");
        LlmClient gemini = new GeminiLlmClient(
                "https://generativelanguage.googleapis.com/v1beta", geminiApiKey, "gemini-3.6-flash");

        Result geminiResult;
        try {
            geminiResult = timedCall("Gemini (gemini-3.6-flash, hosted)", gemini, messages);
        } catch (HttpStatusCodeException e) {
            log.warn("Gemini call failed with HTTP {}: {}. Skipping comparison — GEMINI_API_KEY " +
                    "is set but not accepted by the endpoint.", e.getStatusCode(), e.getResponseBodyAsString());
            Assumptions.assumeTrue(false, "Skipping provider comparison: GEMINI_API_KEY not recognized ("
                    + e.getStatusCode() + ")");
            return;
        }

        log.info("=== Provider comparison for prompt: \"{}\" ===", PROMPT);
        log.info("{}", ollamaResult);
        log.info("{}", geminiResult);
    }

    private Result timedCall(String label, LlmClient client, List<ChatMessage> messages) {
        long start = System.nanoTime();
        LlmResponse response = client.chat(messages);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertNotNull(response, label + ": response should not be null");
        assertFalse(response.content().isBlank(), label + ": content should not be blank");

        return new Result(label, elapsedMs, response.inputTokens(), response.outputTokens());
    }

    private record Result(String label, long latencyMs, int inputTokens, int outputTokens) {
        @Override
        public String toString() {
            return String.format("%-32s latency=%5dms  inputTokens=%3d  outputTokens=%3d",
                    label, latencyMs, inputTokens, outputTokens);
        }
    }
}
