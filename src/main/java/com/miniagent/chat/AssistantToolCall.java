package com.miniagent.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AssistantToolCall(
        String id,
        String type,
        @JsonProperty("function") ToolFunction function) {

    public record ToolFunction(String name, String arguments) {}

    public static AssistantToolCall function(String id, String name, String arguments) {
        return new AssistantToolCall(id, "function", new ToolFunction(name, arguments));
    }

    public Map<String, Object> toApiMap() {
        return Map.of(
                "id", id,
                "type", type == null ? "function" : type,
                "function", Map.of("name", function.name(), "arguments", function.arguments()));
    }
}
