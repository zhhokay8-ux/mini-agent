package com.miniagent.chat;

import java.util.List;

public record ChatResponse(String model, String content, List<ToolTrace> toolTraces, String sessionId) {

    public ChatResponse(String model, String content) {
        this(model, content, List.of(), null);
    }

    public ChatResponse(String model, String content, List<ToolTrace> toolTraces) {
        this(model, content, toolTraces, null);
    }

    /** 在已有回复上补上 sessionId，不改模型输出。 */
    public ChatResponse withSession(String id) {
        return new ChatResponse(model, content, toolTraces, id);
    }
}
