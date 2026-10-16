package com.miniagent.rag;

/**
 * 入库后的一块知识。
 *
 * @param id 展示给模型的编号，如 C1，回答里用 [C1] 引用
 * @param source 来自哪个文件
 * @param text 切出来的正文
 * @param embedding 这块文本的向量，检索时和问题向量算余弦
 */
public record TextChunk(String id, String source, String text, float[] embedding) {}
