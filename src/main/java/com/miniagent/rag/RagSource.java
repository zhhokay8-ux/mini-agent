package com.miniagent.rag;

/**
 * 一次检索命中，会进 RAG 的 Prompt，也会原样返回给调用方方便核对出处。
 *
 * @param id 块编号，与 {@link TextChunk#id()} 相同
 * @param source 文件名
 * @param score 与问题的余弦相似度，越大越相关（哈希向量只是近似）
 * @param text 块正文
 */
public record RagSource(String id, String source, double score, String text) {}
