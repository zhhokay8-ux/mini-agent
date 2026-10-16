package com.miniagent.chat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeepSeekChatClientTest {

    @Test
    void chatFailsWhenApiKeyMissing() {
        DeepSeekProperties properties =
                new DeepSeekProperties("", "https://api.deepseek.com", "deepseek-chat", Duration.ofSeconds(5));
        DeepSeekChatClient client = new DeepSeekChatClient(properties, new ObjectMapper());

        assertThatThrownBy(() -> client.chat(List.of(new ChatMessage("user", "hi"))))
                .isInstanceOf(MissingApiKeyException.class);
    }
}
