package com.miniagent.chat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniagent.tool.ToolRegistry;
import com.miniagent.tool.WeatherTool;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeepSeekChatClientTest {

    @Test
    void chatFailsWhenApiKeyMissing() {
        DeepSeekProperties properties = new DeepSeekProperties(
                "", "https://api.deepseek.com", "deepseek-chat", Duration.ofSeconds(5), 5);
        ToolRegistry registry = new ToolRegistry(new ObjectMapper(), List.of(new WeatherTool()));
        DeepSeekChatClient client = new DeepSeekChatClient(properties, new ObjectMapper(), registry);

        assertThatThrownBy(() -> client.chat(List.of(new ChatMessage("user", "hi"))))
                .isInstanceOf(MissingApiKeyException.class);
    }
}
