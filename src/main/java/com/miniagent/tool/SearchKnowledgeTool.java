package com.miniagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.miniagent.rag.InMemoryKnowledgeStore;
import com.miniagent.rag.QueryRewriter;
import com.miniagent.rag.RagProperties;
import com.miniagent.rag.RagSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 把第 3 阶段的向量检索暴露成 Agent 工具。制度/退货政策必须先调它，而不是走固定 /api/rag 流水线。
 */
@Component
public class SearchKnowledgeTool implements AgentTool {

    private final InMemoryKnowledgeStore knowledgeStore;
    private final QueryRewriter queryRewriter;
    private final RagProperties ragProperties;

    public SearchKnowledgeTool(
            InMemoryKnowledgeStore knowledgeStore, QueryRewriter queryRewriter, RagProperties ragProperties) {
        this.knowledgeStore = knowledgeStore;
        this.queryRewriter = queryRewriter;
        this.ragProperties = ragProperties;
    }

    @Override
    public String name() {
        return "search_knowledge";
    }

    @Override
    public String description() {
        return "检索公司制度知识库（差旅报销、请假、账号安全、7 天退货政策）。"
                + "用户问政策、能否退货、如何申请制度类问题时必须先调用本工具，不要凭记忆长篇回答。"
                + "不要用它查具体订单。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("type", "string");
        query.put("description", "检索语句，尽量写成制度用语，例如「耳机退货政策」「差旅费报销流程」");
        return Map.of("query", query);
    }

    @Override
    public List<String> required() {
        return List.of("query");
    }

    /**
     * 先口语改写再 TopK 检索，把块编号、来源、正文回给模型。
     * 这里只检索，不调用 LLM 生成最终答案。
     */
    @Override
    public String execute(JsonNode arguments) {
        String query = arguments.path("query").asText("").trim();
        if (!StringUtils.hasText(query)) {
            return ToolJson.error("query is required");
        }
        String rewritten = queryRewriter.rewrite(query);
        List<RagSource> hits = knowledgeStore.search(rewritten, ragProperties.topK());
        List<Map<String, Object>> chunks = hits.stream()
                .map(hit -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", hit.id());
                    row.put("source", hit.source());
                    row.put("score", Math.round(hit.score() * 1000.0) / 1000.0);
                    row.put("text", hit.text());
                    return row;
                })
                .toList();
        return ToolJson.ok(Map.of("rewrittenQuery", rewritten, "chunks", chunks));
    }
}
