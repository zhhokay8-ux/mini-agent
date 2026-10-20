package com.miniagent.chat;

import com.miniagent.shop.PendingConfirmation;
import java.util.List;

/**
 * @param confirmation 写工具触发 HITL 时非空；前端应展示 preview，确认前不要当成已经退货成功
 */
public record ChatResponse(
        String model,
        String content,
        List<ToolTrace> toolTraces,
        String sessionId,
        PendingConfirmation confirmation) {

    public ChatResponse(String model, String content) {
        this(model, content, List.of(), null, null);
    }

    public ChatResponse(String model, String content, List<ToolTrace> toolTraces) {
        this(model, content, toolTraces, null, null);
    }

    /** 在已有回复上补上 sessionId，确认单原样保留。 */
    public ChatResponse withSession(String id) {
        return new ChatResponse(model, content, toolTraces, id, confirmation);
    }
}
