package com.miniagent.rag;

import java.util.List;

/**
 * RAG 一次问答的结果。
 *
 * @param model 生成答案用的聊天模型
 * @param rewrittenQuery 实际拿去检索的问题（可能经过口语改写）
 * @param answer 模型生成的回答
 * @param sources 检索到的 TopK 片段，用来对照「召回对了没有」
 */
public record RagResponse(String model, String rewrittenQuery, String answer, List<RagSource> sources) {}
