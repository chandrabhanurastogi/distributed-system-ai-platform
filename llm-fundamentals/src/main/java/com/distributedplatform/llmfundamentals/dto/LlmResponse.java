package com.distributedplatform.llmfundamentals.dto;

public record LlmResponse(String content, int inputTokens, int outputTokens) {
}
