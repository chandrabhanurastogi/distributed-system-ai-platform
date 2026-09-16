package com.distributedplatform.llmfundamentals.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Tool {
    private String type;
    private Function function;

    public Tool(String name, String description, Map<String, Object> parameters) {
        this("function", new Function(name, description, parameters));
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Function {
        private String name;
        private String description;
        private Map<String, Object> parameters;
    }
}
