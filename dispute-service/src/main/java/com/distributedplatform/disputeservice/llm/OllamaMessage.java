package com.distributedplatform.disputeservice.llm;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Local wire-format type for Ollama's /api/chat message shape - deliberately separate
 * from common.llm.ChatMessage, even though the two happen to have identical fields
 * today. That coincidence is exactly what made it easy to collapse them together by
 * accident; keeping them distinct is what lets this wire format grow (e.g. Ollama's
 * own tool_calls field, if this client ever needed it) without that change rippling
 * into the shared, provider-agnostic ChatMessage that llm-fundamentals also depends on.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OllamaMessage {
    private String role;
    private String content;
}
