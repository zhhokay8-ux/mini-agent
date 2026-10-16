package com.miniagent.chat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * @param sessionId 可选。有则把本轮写入该会话，并用「摘要 + 最近 N 轮」代替裸 messages 调模型
 * @param messages 本轮要追加的消息；有 session 时一般只传一条 user
 */
public record ChatRequest(String sessionId, @NotEmpty @Valid List<ChatMessage> messages) {}
