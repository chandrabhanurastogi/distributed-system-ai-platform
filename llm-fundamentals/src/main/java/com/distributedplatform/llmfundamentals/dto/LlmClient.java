package com.distributedplatform.llmfundamentals.dto;

import java.util.List;

public interface LlmClient {
    LlmResponse chat(List<ChatMessage> messages);
}
