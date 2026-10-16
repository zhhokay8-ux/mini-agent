package com.miniagent.web;

import com.miniagent.chat.ChatClient;
import com.miniagent.chat.ChatMessage;
import com.miniagent.chat.ChatRequest;
import com.miniagent.chat.ChatResponse;
import com.miniagent.session.SessionMemoryService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatClient chatClient;
    private final SessionMemoryService sessions;
    private final SseEvents sseEvents;

    public ChatController(ChatClient chatClient, SessionMemoryService sessions, SseEvents sseEvents) {
        this.chatClient = chatClient;
        this.sessions = sessions;
        this.sseEvents = sseEvents;
    }

    /** 非流式对话。带 sessionId 时用摘要+最近 N 轮，并写回助手回复。 */
    @PostMapping
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        ChatResponse response = chatClient.chat(modelInput(request));
        persistAssistant(request.sessionId(), response.content());
        return response.withSession(request.sessionId());
    }

    /**
     * SSE：先 {@code token}（工具循环结束后的整段回答），再 {@code done}。
     * 天气工具仍须跑完再输出，所以这里不是逐 token。
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@Valid @RequestBody ChatRequest request) {
        ChatResponse response = chatClient.chat(modelInput(request));
        persistAssistant(request.sessionId(), response.content());
        return Flux.just(sseEvents.token(response.content()), sseEvents.done());
    }

    /**
     * 有会话则先把本轮 user/assistant 写入库，再取出「摘要 + 最近 N 轮」给模型。
     * 无会话则直接用请求体里的 messages。
     */
    private List<ChatMessage> modelInput(ChatRequest request) {
        if (!StringUtils.hasText(request.sessionId())) {
            return request.messages();
        }
        for (ChatMessage message : request.messages()) {
            if ("user".equals(message.role()) || "assistant".equals(message.role())) {
                sessions.append(request.sessionId(), message.role(), message.content());
            }
        }
        return sessions.messagesForModel(request.sessionId(), request.messages());
    }

    private void persistAssistant(String sessionId, String content) {
        if (StringUtils.hasText(sessionId)) {
            sessions.append(sessionId, "assistant", content == null ? "" : content);
        }
    }
}
