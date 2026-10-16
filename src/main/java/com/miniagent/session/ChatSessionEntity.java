package com.miniagent.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 一张会话：id + 旧轮次压缩后的摘要。消息在 {@link SessionMessageEntity}。 */
@Entity
@Table(name = "chat_session")
public class ChatSessionEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(length = 4000)
    private String summary;

    private Instant updatedAt;

    protected ChatSessionEntity() {}

    public ChatSessionEntity(String id, Instant updatedAt) {
        this.id = id;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
