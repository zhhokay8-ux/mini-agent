package com.miniagent.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;

/** 工具返回值统一打成 JSON 字符串，避免手写拼接漏转义。 */
public final class ToolJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ToolJson() {}

    /** 成功结果，可附带任意字段（如 city、weather、need_confirm）。 */
    public static String ok(Map<String, ?> fields) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        if (fields != null) {
            body.putAll(fields);
        }
        return write(body);
    }

    /** 失败结果，模型会读到 error 再决定下一步，HTTP 仍然 200。 */
    public static String error(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", false);
        body.put("error", message);
        return write(body);
    }

    private static String write(Map<String, Object> body) {
        try {
            return MAPPER.writeValueAsString(body);
        } catch (JsonProcessingException ex) {
            return "{\"ok\":false,\"error\":\"json serialize failed\"}";
        }
    }
}
