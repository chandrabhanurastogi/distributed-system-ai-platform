package com.distributedplatform.llmfundamentals.service;

import com.distributedplatform.llmfundamentals.dto.LlmClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class LlmProviderConditionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class, OllamaLlmClient.class, GeminiLlmClient.class);

    @Configuration
    static class TestConfig {
        @Bean
        OllamaChatService ollamaChatService() {
            return mock(OllamaChatService.class);
        }
    }

    @Test
    void default_whenPropertyMissing_registersOllamaClientOnly() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(LlmClient.class);
            assertThat(context).hasSingleBean(OllamaLlmClient.class);
            assertThat(context).doesNotHaveBean(GeminiLlmClient.class);
        });
    }

    @Test
    void whenProviderIsOllama_registersOllamaClientOnly() {
        contextRunner
                .withPropertyValues("llm.provider=ollama")
                .run(context -> {
                    assertThat(context).hasSingleBean(LlmClient.class);
                    assertThat(context).hasSingleBean(OllamaLlmClient.class);
                    assertThat(context).doesNotHaveBean(GeminiLlmClient.class);
                });
    }

    @Test
    void whenProviderIsGemini_registersGeminiClientOnly() {
        contextRunner
                .withPropertyValues("llm.provider=gemini")
                .run(context -> {
                    assertThat(context).hasSingleBean(LlmClient.class);
                    assertThat(context).hasSingleBean(GeminiLlmClient.class);
                    assertThat(context).doesNotHaveBean(OllamaLlmClient.class);
                });
    }
}
