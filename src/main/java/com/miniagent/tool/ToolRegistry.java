package com.miniagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ToolRegistry {

    private final ObjectMapper objectMapper;
    private final Map<String, AgentTool> tools;

    public ToolRegistry(ObjectMapper objectMapper, List<AgentTool> tools) {
        this.objectMapper = objectMapper;
        this.tools = tools.stream()
                .collect(Collectors.toUnmodifiableMap(AgentTool::name, Function.identity(), (a, b) -> a));
    }

    public List<Map<String, Object>> openAiToolSpecs() {
        return tools.values().stream().map(this::toSpec).toList();
    }

    public String execute(String name, String argumentsJson) {
        AgentTool tool = tools.get(name);
        if (tool == null) {
            return "{\"ok\":false,\"error\":\"unknown tool: " + name + "\"}";
        }
        JsonNode args;
        try {
            String raw = (argumentsJson == null || argumentsJson.isBlank()) ? "{}" : argumentsJson;
            args = objectMapper.readTree(raw);
            if (!args.isObject()) {
                return "{\"ok\":false,\"error\":\"arguments must be a JSON object\"}";
            }
        } catch (Exception ex) {
            return "{\"ok\":false,\"error\":\"arguments is not valid JSON\"}";
        }
        try {
            return tool.execute(args);
        } catch (Exception ex) {
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            return "{\"ok\":false,\"error\":" + quote(message) + "}";
        }
    }

    /**
     * 把一个 {@link AgentTool} 转成 DeepSeek / OpenAI 请求体里 {@code tools} 数组的一项。
     * 外层 {@code type=function}，内层带上 name、description，以及 JSON Schema 形态的 parameters
     * （object + properties + required），供模型决定是否调用、如何填参。此处只组 Schema，不执行工具。
     */
    private Map<String, Object> toSpec(AgentTool tool) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", tool.parameters());
        parameters.put("required", tool.required());

        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", tool.name());
        function.put("description", tool.description());
        function.put("parameters", parameters);

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("type", "function");
        spec.put("function", function);
        return spec;
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
