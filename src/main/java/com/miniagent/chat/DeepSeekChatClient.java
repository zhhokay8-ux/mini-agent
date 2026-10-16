package com.miniagent.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniagent.tool.ToolRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

@Component
public class DeepSeekChatClient implements ChatClient {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekChatClient.class);

    private final DeepSeekProperties properties;
    private final ObjectMapper objectMapper;
    private final ToolRegistry toolRegistry;
    private final WebClient webClient;

    public DeepSeekChatClient(
            DeepSeekProperties properties, ObjectMapper objectMapper, ToolRegistry toolRegistry) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.toolRegistry = toolRegistry;
        this.webClient = WebClient.builder()
                .baseUrl(properties.baseUrl())
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(4 * 1024 * 1024))
                .build();
    }

    @Override
    public ChatResponse chat(List<ChatMessage> messages) {
        requireApiKey();
        List<ChatMessage> conversation = new ArrayList<>(messages);
        List<ToolTrace> traces = new ArrayList<>();
        String model = properties.model();
        int maxRounds = properties.maxIterations();

        for (int round = 1; round <= maxRounds; round++) {
            JsonNode root = complete(conversation, false, true);
            JsonNode choice = root.path("choices").path(0);
            JsonNode message = choice.path("message");
            model = root.path("model").asText(model);
            List<AssistantToolCall> toolCalls = parseToolCalls(message.path("tool_calls"));
            if (toolCalls.isEmpty()) {
                return new ChatResponse(model, message.path("content").asText(""), List.copyOf(traces));
            }

            //将用户填入的内容和tool记录到chatMessage中
            conversation.add(ChatMessage.assistantToolCalls(nullIfBlank(message.path("content").asText(null)), toolCalls));
            for (AssistantToolCall call : toolCalls) {
                long started = System.nanoTime();
                String name = call.function() == null ? "" : call.function().name();
                String arguments = call.function() == null ? "{}" : call.function().arguments();
                String result = toolRegistry.execute(name, arguments);
                long durationMs = (System.nanoTime() - started) / 1_000_000;
                boolean success = !result.contains("\"ok\":false");
                traces.add(new ToolTrace(round, name, arguments, durationMs, success));
                log.info(
                        "tool round={} name={} arguments={} durationMs={} success={}",
                        round,
                        name,
                        arguments,
                        durationMs,
                        success);
                //将当前call的id和结果加入当当前message中
                conversation.add(ChatMessage.tool(call.id(), result));
            }
        }

        log.warn("tool loop stopped at maxIterations={}", maxRounds);
        return new ChatResponse(model, "已达到最大工具调用轮次（" + maxRounds + "），停止继续调用。", List.copyOf(traces));
    }

    /**
     * Agent 流式：工具循环仍一次性跑完（工具不便边调边流），把最终回答当作一个 token 推出。
     * 真正的逐 token 见 {@link #streamGenerate}。
     */
    @Override
    public Flux<String> stream(List<ChatMessage> messages) {
        ChatResponse response = chat(messages);
        if (response.content() == null || response.content().isBlank()) {
            return Flux.empty();
        }
        return Flux.just(response.content());
    }

    /**
     * RAG 用的单轮调用：{@code withTools=false}，模型只能根据 messages 里已有的资料写答案。
     */
    @Override
    public ChatResponse generate(List<ChatMessage> messages) {
        requireApiKey();
        JsonNode root = complete(messages, false, false);
        JsonNode message = root.path("choices").path(0).path("message");
        String model = root.path("model").asText(properties.model());
        return new ChatResponse(model, message.path("content").asText(""), List.of());
    }

    /**
     * DeepSeek {@code stream=true} 且不带 tools，解析 SSE 里 delta.content 作为 token。
     */
    @Override
    public Flux<String> streamGenerate(List<ChatMessage> messages) {
        requireApiKey();
        return webClient
                .post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(payload(messages, true, false))
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

    /**
     * 解析 DeepSeek 流式 JSON：只取出 {@code choices[0].delta.content}，忽略 [DONE] 和思考字段。
     */
    private Flux<String> extractDelta(String event) {
        if (event == null || event.isBlank() || "[DONE]".equals(event.trim())) {
            return Flux.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(event);
            String content = root.path("choices").path(0).path("delta").path("content").asText("");
            return content.isEmpty() ? Flux.empty() : Flux.just(content);
        } catch (Exception ex) {
            log.debug("Skip non-JSON SSE chunk: {}", event);
            return Flux.empty();
        }
    }

    /**
     * 调用 DeepSeek {@code /chat/completions}。
     *
     * @param withTools true 时带上天气等工具 Schema（Agent 循环）；false 时纯对话（RAG）
     */
    private JsonNode complete(List<ChatMessage> messages, boolean stream, boolean withTools) {
        try {
            JsonNode root = webClient
                    .post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload(messages, stream, withTools))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(properties.timeout());
            if (root == null) {
                throw new IllegalStateException("DeepSeek returned an empty body");
            }
            return root;
        } catch (WebClientResponseException ex) {
            throw new DeepSeekException(
                    "DeepSeek chat failed: HTTP " + ex.getStatusCode().value() + " " + ex.getResponseBodyAsString(),
                    ex);
        }
    }

    /** 组装请求体。只有 withTools 为 true 才放入 {@code tools}/{@code tool_choice}。 */
    private Map<String, Object> payload(List<ChatMessage> messages, boolean stream, boolean withTools) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("messages", messages);
        body.put("stream", stream);
        if (withTools) {
            List<Map<String, Object>> tools = toolRegistry.openAiToolSpecs();
            if (!tools.isEmpty()) {
                body.put("tools", tools);
                body.put("tool_choice", "auto");
            }
        }
        return body;
    }

    /**
     * 把模型返回的 {@code message.tool_calls} 转成 {@link AssistantToolCall} 列表。
     * 节点缺失、不是数组或为空时，表示这一轮没有工具调用，模型已经给出最终 {@code content}。
     */
    private List<AssistantToolCall> parseToolCalls(JsonNode node) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            return List.of();
        }
        List<AssistantToolCall> calls = new ArrayList<>();
        for (JsonNode item : node) {
            // 每项形如 {id, type, function:{name, arguments}}，arguments 仍是 JSON 字符串
            calls.add(objectMapper.convertValue(item, AssistantToolCall.class));
        }
        return List.copyOf(calls);
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

    private static String nullIfBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
