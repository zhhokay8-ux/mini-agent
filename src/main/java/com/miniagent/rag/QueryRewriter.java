package com.miniagent.rag;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 口语 query 直接检索效果差，本阶段用固定映射做最小改写。
 * 例如「报销咋整」→「差旅费报销流程」，让向量更接近制度文档里的用词。
 */
@Component
public class QueryRewriter {

    private static final Map<String, String> ALIASES = new LinkedHashMap<>();

    static {
        ALIASES.put("报销咋整", "差旅费报销流程");
        ALIASES.put("报销怎么弄", "差旅费报销流程");
        ALIASES.put("怎么报销", "差旅费报销申请流程");
        ALIASES.put("出差报销", "差旅费报销标准与流程");
        ALIASES.put("请假咋整", "事假年假申请流程");
        ALIASES.put("怎么请假", "事假年假申请流程");
    }

    /**
     * 若问题包含别名表中的口语，返回对应的检索用语；否则原样返回（trim 后）。
     * 命中多条时取 {@link LinkedHashMap} 中先声明的那条。
     */
    public String rewrite(String question) {
        if (question == null) {
            return "";
        }
        String trimmed = question.trim();
        for (Map.Entry<String, String> entry : ALIASES.entrySet()) {
            if (trimmed.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return trimmed;
    }
}
