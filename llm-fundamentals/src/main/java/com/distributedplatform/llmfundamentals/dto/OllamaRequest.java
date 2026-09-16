package com.distributedplatform.llmfundamentals.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OllamaRequest {
    private String model;
    private List<Message> messages;
    private boolean stream;
    private Map<String, Object> format;
}
