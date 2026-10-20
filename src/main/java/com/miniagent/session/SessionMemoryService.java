package com.miniagent.session;

import com.miniagent.chat.ChatClient;
import com.miniagent.chat.ChatMessage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 会话记忆：库里保存全量 user/assistant；送给模型时只有「摘要 + 最近 N 轮」。
 * 超过窗口的旧消息用 LLM 压成摘要，这样 20 轮后请求体不会按轮数线性膨胀。
 */
@Service
public class SessionMemoryService {

    private static final Logger log = LoggerFactory.getLogger(SessionMemoryService.class);

    private final ChatSessionRepository sessions;
    private final SessionMessageRepository messages;
    private final SessionProperties properties;
    private final ChatClient chatClient;
    private final TokenEstimator tokenEstimator;

    public SessionMemoryService(
            ChatSessionRepository sessions,
            SessionMessageRepository messages,
            SessionProperties properties,
            ChatClient chatClient,
            TokenEstimator tokenEstimator) {
        this.sessions = sessions;
        this.messages = messages;
        this.properties = properties;
        this.chatClient = chatClient;
        this.tokenEstimator = tokenEstimator;
    }

    /** 新建空会话，返回 UUID，后续 chat/rag 都带上这个 id。 */
    @Transactional
    public String create() {
        String id = UUID.randomUUID().toString();
        sessions.save(new ChatSessionEntity(id, Instant.now()));
        return id;
    }

    /** 追加一条用户或助手消息，并尝试把窗口外的旧消息压进摘要。 */
    @Transactional
    public void append(String sessionId, String role, String content) {
        ChatSessionEntity session = requireSession(sessionId);
        messages.save(new SessionMessageEntity(sessionId, role, nullToEmpty(content), Instant.now()));
        session.setUpdatedAt(Instant.now());
        sessions.save(session);
        //压缩会话-把窗口外的旧消息压进摘要
        compactIfNeeded(session);
    }

    /**
     * 组装真正发给模型的 messages：可选的调用方 system → 会话摘要 system → 最近 N 轮。
     * 当前这一轮的用户话要先 {@link #append} 再调本方法。
     */
    @Transactional
    public List<ChatMessage> messagesForModel(String sessionId, List<ChatMessage> extraSystem) {
        ChatSessionEntity session = requireSession(sessionId);
        List<SessionMessageEntity> all = messages.findBySessionIdOrderByIdAsc(sessionId);
        List<SessionMessageEntity> recent = SessionWindow.recent(all, properties.maxRecentMessages());

        List<ChatMessage> output = new ArrayList<>();
        if (extraSystem != null) {
            for (ChatMessage message : extraSystem) {
                if ("system".equals(message.role())) {
                    output.add(message);
                }
            }
        }
        if (StringUtils.hasText(session.getSummary())) {
            output.add(new ChatMessage("system", "以下是本会话更早轮次的摘要，请在追问时遵守其中已确认的事实：\n" + session.getSummary()));
        }
        for (SessionMessageEntity row : recent) {
            output.add(new ChatMessage(row.getRole(), row.getContent()));
        }
        int approxTokens = tokenEstimator.estimateMessages(output);
        log.info(
                "session={} recentTurns={} totalStored={} summaryChars={} approxTokens={}",
                sessionId,
                recent.size() / 2,
                all.size(),
                session.getSummary() == null ? 0 : session.getSummary().length(),
                approxTokens);
        return output;
    }

    /**
     * 当前用户话尚未 append 时，取出已有对话的最近几条，拼进 RAG Prompt，
     * 让模型知道「那怎么申请」的「那」指什么。
     */
    @Transactional(readOnly = true)
    public String historyPreview(String sessionId, int maxMessages) {
        requireSession(sessionId);
        List<SessionMessageEntity> recent =
                SessionWindow.recent(messages.findBySessionIdOrderByIdAsc(sessionId), maxMessages);
        StringBuilder text = new StringBuilder();
        for (SessionMessageEntity row : recent) {
            text.append(row.getRole()).append(": ").append(row.getContent()).append('\n');
        }
        return text.toString();
    }

    /** 最近一条用户问题，用来给 RAG 追问拼检索词。没有则 null。 */
    @Transactional(readOnly = true)
    public String lastUserQuestion(String sessionId) {
        requireSession(sessionId);
        List<SessionMessageEntity> all = messages.findBySessionIdOrderByIdAsc(sessionId);
        for (int i = all.size() - 1; i >= 0; i--) {
            if ("user".equals(all.get(i).getRole())) {
                return all.get(i).getContent();
            }
        }
        return null;
    }

    /**
     * RAG 检索用语。{@code previousUserQuestion} 必须是 <b>本轮 append 之前</b> 的上一条用户问题，
     * 否则会把当前这句拼进去，追问检索就漂了。
     */
    public String searchQuery(String question, String rewritten, String previousUserQuestion) {
        if (!SessionWindow.isFollowUp(question) || !StringUtils.hasText(previousUserQuestion)) {
            return rewritten;
        }
        return previousUserQuestion + " " + rewritten;
    }

    /**
     * 最近 {@code maxRecentMessages} 条继续原样送给模型；窗口外且尚未压缩的旧消息交给 LLM 压成摘要。
     * 已有摘要会一并送进去滚动更新。压完后把这些旧消息标成 compacted，避免下一轮重复压缩。
     * 消息还没超出窗口时直接返回。
     */
    private void compactIfNeeded(ChatSessionEntity session) {
        List<SessionMessageEntity> all = messages.findBySessionIdOrderByIdAsc(session.getId());
        List<SessionMessageEntity> stale = SessionWindow.staleUncompacted(all, properties.maxRecentMessages());
        if (stale.isEmpty()) {
            return;
        }
        StringBuilder history = new StringBuilder();
        if (StringUtils.hasText(session.getSummary())) {
            history.append("已有摘要：\n").append(session.getSummary()).append("\n\n");
        }
        history.append("需要压缩的旧对话：\n");
        for (SessionMessageEntity row : stale) {
            history.append(row.getRole()).append(": ").append(row.getContent()).append("\n");
        }
        String summary = chatClient
                .generate(List.of(
                        new ChatMessage(
                                "system",
                                "把对话压成不超过 "
                                        + properties.summaryMaxChars()
                                        + " 字的中文摘要，保留主题、关键事实和未完成事项，不要列举每一句。"),
                        new ChatMessage("user", history.toString())))
                .content();
        if (summary != null && summary.length() > properties.summaryMaxChars()) {
            summary = summary.substring(0, properties.summaryMaxChars());
        }
        session.setSummary(summary);
        session.setUpdatedAt(Instant.now());
        sessions.save(session);
        for (SessionMessageEntity row : stale) {
            row.setCompacted(true);
        }
        messages.saveAll(stale);
        log.info("session={} compacted {} old messages, summaryChars={}", session.getId(), stale.size(), summary == null ? 0 : summary.length());
    }

    private ChatSessionEntity requireSession(String sessionId) {
        return sessions.findById(sessionId).orElseThrow(() -> new SessionNotFoundException(sessionId));
    }

    private static String nullToEmpty(String content) {
        return content == null ? "" : content;
    }
}
