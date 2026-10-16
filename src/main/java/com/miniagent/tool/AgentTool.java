package com.miniagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

/**
 * Agent 可调用的工具。{@code name}/{@code description}/{@code parameters}/{@code required}
 * 会组装成发给模型的 JSON Schema；真正执行发生在 {@link #execute(JsonNode)}，且必须在服务端校验参数。
 */
public interface AgentTool {

    /** 工具唯一标识，须与模型 {@code tool_calls.function.name} 一致，例如 {@code get_weather}。 */
    String name();

    /** 给模型看的说明：何时该用、不要用在什么问题上，影响会不会选中这个工具。 */
    String description();

    /**
     * JSON Schema 的 {@code properties}：每个入参的类型和含义。
     * 只描述契约，不执行逻辑。
     */
    Map<String, Object> parameters();

    /** 必填参数名列表，对应 Schema 的 {@code required}，缺参时应在 {@link #execute} 里拒绝。 */
    List<String> required();

    /**
     * 用模型给出的参数真正执行工具，返回给下一轮对话的字符串（建议 JSON）。
     * 失败时返回错误信息，不要把异常抛到 HTTP 层，以便模型根据错误继续作答。
     */
    String execute(JsonNode arguments);
}
