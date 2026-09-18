package com.distributedplatform.llmfundamentals.dto.ollama;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OllamaRequest {
    private String model;
    private List<Message> messages;
    private boolean stream;
    private Map<String, Object> format;
    private List<Tool> tools;

    public OllamaRequest(String model, List<Message> messages, boolean stream) {
        this(model, messages, stream, null, null);
    }

    public OllamaRequest(String model, List<Message> messages, boolean stream, Map<String, Object> format) {
        this(model, messages, stream, format, null);
    }
}
