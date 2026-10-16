package com.miniagent.chat;

import java.util.List;
import reactor.core.publisher.Flux;

public interface ChatClient {

    ChatResponse chat(List<ChatMessage> messages);

    Flux<String> stream(List<ChatMessage> messages);

    /**
     * 单轮补全，请求里不带 tools。
     * RAG 必须走这个方法：若走 {@link #chat}，模型可能去调天气工具而不是根据资料回答。
     */
    ChatResponse generate(List<ChatMessage> messages);
}
