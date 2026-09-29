package com.distributedplatform.common.llm;

public record LlmResponse(String content, int inputTokens, int outputTokens) {
}
