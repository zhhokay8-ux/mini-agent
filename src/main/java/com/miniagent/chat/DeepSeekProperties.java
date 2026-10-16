package com.miniagent.chat;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "deepseek")
public record DeepSeekProperties(
        String apiKey, String baseUrl, String model, Duration timeout, int maxIterations) {

    public DeepSeekProperties {
        if (maxIterations <= 0) {
            maxIterations = 5;
        }
    }

    public boolean hasApiKey() {
        return StringUtils.hasText(apiKey);
    }
}
