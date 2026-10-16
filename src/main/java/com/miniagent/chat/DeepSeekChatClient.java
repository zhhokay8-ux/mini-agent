package com.miniagent.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class DeepSeekChatClient implements ChatClient {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekChatClient.class);

    private final DeepSeekProperties properties;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    public DeepSeekChatClient(DeepSeekProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(properties.baseUrl())
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(4 * 1024 * 1024))
                .build();
    }

    @Override
    public ChatResponse chat(List<ChatMessage> messages) {
        requireApiKey();
        try {
            JsonNode root = webClient
                    .post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload(messages, false))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(properties.timeout());
            if (root == null) {
                throw new IllegalStateException("DeepSeek returned an empty body");
            }
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            String model = root.path("model").asText(properties.model());
            return new ChatResponse(model, content);
        } catch (WebClientResponseException ex) {
            throw new DeepSeekException(
                    "DeepSeek chat failed: HTTP " + ex.getStatusCode().value() + " " + ex.getResponseBodyAsString(),
                    ex);
        }
    }

    @Override
    public Flux<String> stream(List<ChatMessage> messages) {
        requireApiKey();
        return webClient
                .post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(payload(messages, true))
                .retrieve()
                .bodyToFlux(String.class)
                .flatMap(this::extractDelta)
                .filter(token -> !token.isEmpty())
                .onErrorMap(
                        WebClientResponseException.class,
                        ex -> new DeepSeekException(
                                "DeepSeek stream failed: HTTP " + ex.getStatusCode().value() + " "
                                        + ex.getResponseBodyAsString(),
                                ex));
    }

    private Map<String, Object> payload(List<ChatMessage> messages, boolean stream) {
        return Map.of(
                "model", properties.model(),
                "messages", messages,
                "stream", stream);
    }

    private Flux<String> extractDelta(String event) {
        if (event == null || event.isBlank() || "[DONE]".equals(event.trim())) {
            return Flux.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(event);
            JsonNode delta = root.path("choices").path(0).path("delta");
            String content = delta.path("content").asText("");
            String reasoning = delta.path("reasoning_content").asText("");
            if (!content.isEmpty()) {
                return Mono.just(content).flux();
            }
            if (!reasoning.isEmpty()) {
                return Flux.empty();
            }
            return Flux.empty();
        } catch (Exception ex) {
            log.debug("Skip non-JSON SSE chunk: {}", event);
            return Flux.empty();
        }
    }

    private void requireApiKey() {
        if (!properties.hasApiKey()) {
            throw new MissingApiKeyException(
                    "Set DEEPSEEK_API_KEY (or deepseek.api-key) before calling /api/chat");
        }
    }

    private String bearer() {
        return "Bearer " + properties.apiKey().trim();
    }
}
