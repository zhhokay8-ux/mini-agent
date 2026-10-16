package com.miniagent.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class WeatherToolTest {

    private final WeatherTool tool = new WeatherTool();
    private final ObjectMapper mapper = new ObjectMapper();
    private final ToolRegistry registry = new ToolRegistry(mapper, java.util.List.of(tool));

    @Test
    void requiresCity() throws Exception {
        String result = tool.execute(mapper.readTree("{}"));
        assertThat(result).contains("city is required");
    }

    @Test
    void returnsMockForecastForBeijing() throws Exception {
        String result = tool.execute(mapper.readTree("{\"city\":\"北京\"}"));
        assertThat(result).contains("\"ok\":true").contains("北京");
    }

    @Test
    void rejectsUnknownCity() throws Exception {
        String result = tool.execute(mapper.readTree("{\"city\":\"火星\"}"));
        assertThat(result).contains("unsupported city");
    }

    @Test
    void providerErrorBecomesToolResultNotException() {
        String result = registry.execute("get_weather", "{\"city\":\"error\"}");
        assertThat(result).contains("\"ok\":false").contains("weather provider timeout");
    }

    @Test
    void invalidJsonIsRejected() {
        String result = registry.execute("get_weather", "not-json");
        assertThat(result).contains("not valid JSON");
    }

    @Test
    void unknownToolIsRejected() {
        String result = registry.execute("launch_missile", "{}");
        assertThat(result).contains("unknown tool");
    }
}
