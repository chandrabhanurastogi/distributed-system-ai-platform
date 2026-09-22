package com.distributedplatform.llmfundamentals.embedding;

public record Document(String id, String text, double[] embedding) {
}
