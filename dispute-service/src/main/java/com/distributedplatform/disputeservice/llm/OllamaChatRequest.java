package com.distributedplatform.disputeservice.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OllamaChatRequest {
    private String model;
    private List<OllamaMessage> messages;
    private boolean stream;
    private Options options;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Options {
        private double temperature;
    }
}
