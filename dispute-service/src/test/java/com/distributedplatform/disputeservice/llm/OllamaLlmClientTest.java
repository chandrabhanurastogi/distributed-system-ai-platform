package com.distributedplatform.disputeservice.llm;

import com.distributedplatform.common.llm.ChatMessage;
import com.distributedplatform.common.llm.LlmResponse;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OllamaLlmClientTest {

    private static final Logger log = LoggerFactory.getLogger(OllamaLlmClientTest.class);

    @Test
    void chat_withRealOllama_returnsFaithfulResponseWithRealTokenCounts() {
        OllamaLlmClient client = new OllamaLlmClient(
                RestClient.builder().baseUrl("http://localhost:11434").build(), "llama3.2");

        List<ChatMessage> messages = List.of(new ChatMessage("user", "Explain recursion in one short sentence."));

        LlmResponse response = client.chat(messages);

        log.info("LlmResponse: content='{}', inputTokens={}, outputTokens={}",
                response.content(), response.inputTokens(), response.outputTokens());

        assertThat(response).isNotNull();
        assertThat(response.content()).isNotBlank();
        assertThat(response.inputTokens()).isGreaterThan(0);
        assertThat(response.outputTokens()).isGreaterThan(0);
    }
}
