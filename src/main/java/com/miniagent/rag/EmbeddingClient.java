package com.miniagent.rag;

/**
 * 把文本变成向量。检索时问题和文档块必须用同一套实现，否则余弦相似度没有意义。
 */
public interface EmbeddingClient {

    /** 返回固定维度的向量；空文本应得到全 0 或等价的空向量。 */
    float[] embed(String text);
}
