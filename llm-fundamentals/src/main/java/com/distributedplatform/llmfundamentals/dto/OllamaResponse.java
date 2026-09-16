package com.distributedplatform.llmfundamentals.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OllamaResponse {
    private String model;
    @JsonProperty(value = "created_at")
    private String createdAt;
    private Message message;
    private boolean done;
    @JsonProperty("done_reason")
    private String doneReason;
    @JsonProperty("total_duration")
    private long totalDuration;
    @JsonProperty("load_duration")
    private long loadDuration;

    //prompt_eval_count: number of input/prompt tokens the model actually processed
    @JsonProperty("prompt_eval_count")
    private long promptEvalCount;
    @JsonProperty("prompt_eval_cached_count")
    private long promptEvalCachedCount;
    @JsonProperty("prompt_eval_duration")
    private long promptEvalDuration;

    //eval_count: the number of tokens it actually generated in the reply.
    @JsonProperty("eval_count")
    private long evalCount;
    @JsonProperty("eval_duration")
    private long evalDuration;
}
