package com.miniagent.chat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatMessage(
        @NotBlank String role,
        String content,
        @JsonProperty("tool_call_id") String toolCallId,
        @JsonProperty("tool_calls") List<AssistantToolCall> toolCalls) {

    public ChatMessage(String role, String content) {
        this(role, content, null, null);
    }

    public static ChatMessage tool(String toolCallId, String content) {
        return new ChatMessage("tool", content, toolCallId, null);
    }

    public static ChatMessage assistantToolCalls(String content, List<AssistantToolCall> toolCalls) {
        return new ChatMessage("assistant", content, null, toolCalls);
    }
}
