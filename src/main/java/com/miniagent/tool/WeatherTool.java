package com.miniagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class WeatherTool implements AgentTool {

    static final String ERROR_CITY = "error";

    private static final Map<String, String> FORECAST = Map.of(
            "北京", "晴，15°C，北风 2 级",
            "上海", "多云，18°C，东风 3 级",
            "广州", "小雨，24°C，湿度 80%",
            "深圳", "阴，23°C，南风 2 级",
            "杭州", "晴转多云，17°C");

    @Override
    public String name() {
        return "get_weather";
    }

    @Override
    public String description() {
        return "查询中国城市的模拟天气。仅支持北京、上海、广州、深圳、杭州。"
                + "当用户问天气、气温、是否下雨时使用。不要用它回答 RAG、Agent 等概念问题。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> city = new LinkedHashMap<>();
        city.put("type", "string");
        city.put("description", "城市中文名，例如北京");
        return Map.of("city", city);
    }

    @Override
    public List<String> required() {
        return List.of("city");
    }

    @Override
    public String execute(JsonNode arguments) {
        String city = arguments.path("city").asText("").trim();
        if (!StringUtils.hasText(city)) {
            return "{\"ok\":false,\"error\":\"city is required\"}";
        }
        if (ERROR_CITY.equalsIgnoreCase(city)) {
            throw new IllegalStateException("weather provider timeout");
        }
        String forecast = FORECAST.get(city);
        if (forecast == null) {
            return "{\"ok\":false,\"error\":\"unsupported city: "
                    + city
                    + "\",\"supported\":[\"北京\",\"上海\",\"广州\",\"深圳\",\"杭州\"]}";
        }
        return "{\"ok\":true,\"city\":\"" + city + "\",\"weather\":\"" + forecast + "\"}";
    }
}
