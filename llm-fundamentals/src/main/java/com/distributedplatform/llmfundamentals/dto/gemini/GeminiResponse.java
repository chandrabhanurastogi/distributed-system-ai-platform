package com.distributedplatform.llmfundamentals.dto.gemini;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GeminiResponse {
    private String id;
    private String status;
    private Usage usage;
    private List<Step> steps;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Usage {
        @JsonProperty("total_input_tokens")
        private int totalInputTokens;

        @JsonProperty("total_output_tokens")
        private int totalOutputTokens;

        @JsonProperty("total_tokens")
        private int totalTokens;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Step {
        private List<ContentItem> content;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ContentItem {
        private String text;
    }
}
