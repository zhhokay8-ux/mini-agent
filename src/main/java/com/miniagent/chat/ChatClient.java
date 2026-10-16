package com.miniagent.chat;

import java.util.List;
import reactor.core.publisher.Flux;

public interface ChatClient {

    ChatResponse chat(List<ChatMessage> messages);

    Flux<String> stream(List<ChatMessage> messages);
}
