package com.miniagent.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;

/** 把第 4 阶段约定的 SSE 事件名统一成 token / sources / done。 */
@Component
public class SseEvents {

    private final ObjectMapper objectMapper;

    public SseEvents(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** {@code event: sources}，data 为 JSON（RAG 的命中片段）。 */
    public ServerSentEvent<String> sources(Object body) {
        return json("sources", body);
    }

    /** {@code event: token}，data 为一小段生成文本。 */
    public ServerSentEvent<String> token(String piece) {
        return ServerSentEvent.<String>builder().event("token").data(piece == null ? "" : piece).build();
    }

    /** {@code event: done}，表示这一轮结束。 */
    public ServerSentEvent<String> done() {
        return json("done", java.util.Map.of("ok", true));
    }

    private ServerSentEvent<String> json(String event, Object body) {
        try {
            return ServerSentEvent.<String>builder()
                    .event(event)
                    .data(objectMapper.writeValueAsString(body))
                    .build();
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize SSE " + event, ex);
        }
    }
}
