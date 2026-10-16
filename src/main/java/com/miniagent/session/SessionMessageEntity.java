package com.miniagent.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 会话里的一条消息。只存最终给用户看的 user/assistant 文本，不存 tool 中间态，避免窗口被工具 JSON 撑满。
 */
@Entity
@Table(name = "session_message")
public class SessionMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 36)
    private String sessionId;

    @Column(nullable = false, length = 32)
    private String role;

    @Column(nullable = false, length = 8000)
    private String content;

    /** true 表示已打进会话摘要，组装模型上下文时不再重复带上。 */
    private boolean compacted;

    private Instant createdAt;

    protected SessionMessageEntity() {}

    public SessionMessageEntity(String sessionId, String role, String content, Instant createdAt) {
        this.sessionId = sessionId;
        this.role = role;
        this.content = content;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public boolean isCompacted() {
        return compacted;
    }

    public void setCompacted(boolean compacted) {
        this.compacted = compacted;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
