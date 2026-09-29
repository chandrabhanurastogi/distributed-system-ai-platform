package com.distributedplatform.disputeservice.llm;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OllamaChatResponse {
    private String model;
    private OllamaMessage message;
    @JsonProperty("prompt_eval_count")
    private long promptEvalCount;
    @JsonProperty("eval_count")
    private long evalCount;
}
