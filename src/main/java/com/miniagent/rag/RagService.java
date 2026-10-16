package com.miniagent.rag;

import com.miniagent.chat.ChatClient;
import com.miniagent.chat.ChatMessage;
import com.miniagent.chat.ChatResponse;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * RAG 编排：改写问题 → 检索 → 把片段塞进 Prompt → 调用 {@link ChatClient#generate} 生成。
 * 这里不用 {@link ChatClient#chat}，避免模型去调天气等工具。
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private static final String SYSTEM_PROMPT =
            """
            你是企业内部知识助手。只能根据用户消息里提供的资料片段回答。
            规则：
            1. 资料足以回答时，用中文简洁作答，并在句末列出依据编号，格式如 [C1][C2]。
            2. 资料没有写到的内容，必须回答「根据现有资料无法确定」，不要编造制度、数字或流程。
            3. 不要使用资料之外的常识去补全公司规定。
            """;

    private final RagProperties properties;
    private final QueryRewriter queryRewriter;
    private final InMemoryKnowledgeStore knowledgeStore;
    private final ChatClient chatClient;

    public RagService(
            RagProperties properties,
            QueryRewriter queryRewriter,
            InMemoryKnowledgeStore knowledgeStore,
            ChatClient chatClient) {
        this.properties = properties;
        this.queryRewriter = queryRewriter;
        this.knowledgeStore = knowledgeStore;
        this.chatClient = chatClient;
    }

    /**
     * 完成一次「检索增强生成」。
     * 日志里的 hits 是块编号和分数：分数高但答案仍胡编，多半是生成没用资料；分数低则是检索没召回。
     */
    public RagResponse ask(String question) {
        String rewritten = queryRewriter.rewrite(question);
        List<RagSource> sources = knowledgeStore.search(rewritten, properties.topK());
        log.info(
                "rag question='{}' rewritten='{}' hits={}",
                question,
                rewritten,
                sources.stream().map(s -> s.id() + ":" + String.format("%.3f", s.score())).toList());

        String userPrompt = buildUserPrompt(question, rewritten, sources);
        ChatResponse generated =
                chatClient.generate(List.of(new ChatMessage("system", SYSTEM_PROMPT), new ChatMessage("user", userPrompt)));
        return new RagResponse(generated.model(), rewritten, generated.content(), sources);
    }

    /** 把原始问题、改写问题和 TopK 资料拼成 user 消息，让模型能同时看到编号与正文。 */
    private static String buildUserPrompt(String question, String rewritten, List<RagSource> sources) {
        String materials = sources.stream()
                .map(source -> "### " + source.id() + "（来源：" + source.source() + "，相似度："
                        + String.format("%.3f", source.score()) + "）\n" + source.text())
                .collect(Collectors.joining("\n\n"));
        return "原始问题：" + question + "\n检索用问题：" + rewritten + "\n\n资料：\n" + materials;
    }
}
