package com.miniagent.web;

import com.miniagent.chat.ChatClient;
import com.miniagent.rag.RagRequest;
import com.miniagent.rag.RagResponse;
import com.miniagent.rag.RagService;
import com.miniagent.rag.RagService.PreparedRag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final RagService ragService;
    private final ChatClient chatClient;
    private final SseEvents sseEvents;

    public RagController(RagService ragService, ChatClient chatClient, SseEvents sseEvents) {
        this.ragService = ragService;
        this.chatClient = chatClient;
        this.sseEvents = sseEvents;
    }

    /** 最小 RAG：同步返回答案和 sources。 */
    @PostMapping
    public RagResponse ask(@Valid @RequestBody RagRequest request) {
        return ragService.ask(request.question(), request.sessionId());
    }

    /**
     * SSE 顺序：{@code sources} → 若干 {@code token} → {@code done}。
     * 全部 token 拼起来后才写入会话，避免流到一半就落一条空助手消息。
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@Valid @RequestBody RagRequest request) {
        PreparedRag prepared = ragService.prepare(request.question(), request.sessionId());
        StringBuilder answer = new StringBuilder();
        return Flux.concat(
                Flux.just(sseEvents.sources(prepared.sources())),
                chatClient.streamGenerate(prepared.modelMessages())
                        .doOnNext(answer::append)
                        .map(sseEvents::token),
                Flux.defer(() -> {
                    ragService.persistTurn(prepared.sessionId(), prepared.currentQuestion(), answer.toString());
                    return Flux.just(sseEvents.done());
                }));
    }
}
