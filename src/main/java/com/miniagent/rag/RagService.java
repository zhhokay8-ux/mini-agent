package com.miniagent.rag;

import com.miniagent.chat.ChatClient;
import com.miniagent.chat.ChatMessage;
import com.miniagent.chat.ChatResponse;
import com.miniagent.session.SessionMemoryService;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * RAG 编排：改写/追问拼接 → 检索 → generate。
 * 第 4 阶段可挂 session：追问检索拼上一轮主题，生成时带最近对话，并落库。
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
            4. 若有对话历史，追问要接上已出现的主题，但仍只能用资料里的规定。
            """;

    private final RagProperties properties;
    private final QueryRewriter queryRewriter;
    private final InMemoryKnowledgeStore knowledgeStore;
    private final ChatClient chatClient;
    private final SessionMemoryService sessions;

    public RagService(
            RagProperties properties,
            QueryRewriter queryRewriter,
            InMemoryKnowledgeStore knowledgeStore,
            ChatClient chatClient,
            SessionMemoryService sessions) {
        this.properties = properties;
        this.queryRewriter = queryRewriter;
        this.knowledgeStore = knowledgeStore;
        this.chatClient = chatClient;
        this.sessions = sessions;
    }

    /**
     * 完成一次「检索增强生成」。有 sessionId 时会在生成后写入 user/assistant，供下一轮追问。
     */
    public RagResponse ask(String question, String sessionId) {
        PreparedRag prepared = prepare(question, sessionId);
        ChatResponse generated = chatClient.generate(prepared.modelMessages());
        persistTurn(sessionId, question, generated.content());
        return new RagResponse(
                generated.model(), prepared.searchQuery(), generated.content(), prepared.sources(), sessionId);
    }

    /**
     * 只做检索和拼 Prompt，不调模型。流式接口先拿到 sources，再 {@link ChatClient#streamGenerate}。
     */
    public PreparedRag prepare(String question, String sessionId) {
        String rewritten = queryRewriter.rewrite(question);
        String previous = StringUtils.hasText(sessionId) ? sessions.lastUserQuestion(sessionId) : null;
        String search = sessions.searchQuery(question, rewritten, previous);
        List<RagSource> sources = knowledgeStore.search(search, properties.topK());
        log.info(
                "rag question='{}' rewritten='{}' search='{}' hits={}",
                question,
                rewritten,
                search,
                sources.stream().map(s -> s.id() + ":" + String.format("%.3f", s.score())).toList());

        String history = StringUtils.hasText(sessionId) ? sessions.historyPreview(sessionId, 8) : "";
        List<ChatMessage> modelMessages = List.of(
                new ChatMessage("system", SYSTEM_PROMPT),
                new ChatMessage("user", buildUserPrompt(question, search, sources, history)));
        return new PreparedRag(search, sources, modelMessages, question, sessionId);
    }

    /** 把本轮问答写入会话。流式要等 token 拼完再调。 */
    public void persistTurn(String sessionId, String question, String answer) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        sessions.append(sessionId, "user", question);
        sessions.append(sessionId, "assistant", answer == null ? "" : answer);
    }

    /**
     * 原始问题、检索词、可选对话历史、TopK 资料拼成一条 user，模型仍只允许依据资料作答。
     */
    private static String buildUserPrompt(String question, String search, List<RagSource> sources, String history) {
        String materials = sources.stream()
                .map(source -> "### " + source.id() + "（来源：" + source.source() + "，相似度："
                        + String.format("%.3f", source.score()) + "）\n" + source.text())
                .collect(Collectors.joining("\n\n"));
        StringBuilder user = new StringBuilder();
        if (StringUtils.hasText(history)) {
            user.append("对话历史：\n").append(history).append('\n');
        }
        user.append("原始问题：")
                .append(question)
                .append("\n检索用问题：")
                .append(search)
                .append("\n\n资料：\n")
                .append(materials);
        return user.toString();
    }

    /**
     * 检索完成后、生成之前的快照，供同步 ask 和 SSE 共用同一套 Prompt。
     *
     * @param currentQuestion 本轮用户原话，流式结束后 persist 用
     */
    public record PreparedRag(
            String searchQuery,
            List<RagSource> sources,
            List<ChatMessage> modelMessages,
            String currentQuestion,
            String sessionId) {}
}
