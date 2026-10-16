package com.miniagent.chat;

import java.util.List;

public record ChatResponse(String model, String content, List<ToolTrace> toolTraces) {

    public ChatResponse(String model, String content) {
        this(model, content, List.of());
    }
}
