package com.distributedplatform.common.llm;

import java.util.List;

public interface LlmClient {
    LlmResponse chat(List<ChatMessage> messages);
}
