package com.miniagent.rag;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code POST /api/rag} 入参。
 *
 * @param sessionId 可选。有则记住问答，追问「那怎么申请」时能拼上上一轮主题再检索
 */
public record RagRequest(String sessionId, @NotBlank String question) {}
