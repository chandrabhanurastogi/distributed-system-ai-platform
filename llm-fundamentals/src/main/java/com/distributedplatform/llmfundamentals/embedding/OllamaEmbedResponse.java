package com.distributedplatform.llmfundamentals.embedding;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OllamaEmbedResponse {
    private String model;
    private List<double[]> embeddings;
    @JsonProperty("total_duration")
    private long totalDuration;
    @JsonProperty("load_duration")
    private long loadDuration;
    @JsonProperty("prompt_eval_count")
    private int promptEvalCount;
}
