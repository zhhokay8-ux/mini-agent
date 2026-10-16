package com.miniagent.rag;

import org.springframework.stereotype.Component;

/**
 * 本地哈希向量，不调用 Embedding API，用来先跑通「切块 → 余弦 TopK → 带出处生成」。
 * 生产应换成真实 Embedding 模型；问题和文档必须同一模型，否则分数不可比。
 */
@Component
public class HashingEmbeddingClient implements EmbeddingClient {

    static final int DIMENSIONS = 256;

    /**
     * 用单字和相邻二字的哈希往 256 维上累加，再 L2 归一化。
     * 相同用词会落到相近维度，只能做教学级相似检索，不能当生产向量库。
     */
    @Override
    public float[] embed(String text) {
        float[] vector = new float[DIMENSIONS];
        if (text == null || text.isBlank()) {
            return vector;
        }
        String normalized = text.toLowerCase().replaceAll("\\s+", "");
        for (int i = 0; i < normalized.length(); i++) {
            add(vector, normalized.charAt(i));
            if (i + 1 < normalized.length()) {
                add(vector, (normalized.charAt(i) * 31) ^ normalized.charAt(i + 1));
            }
        }
        return l2Normalize(vector);
    }

    /** 把 token 哈希到某一维并 +1，相当于极简词袋。 */
    private static void add(float[] vector, int token) {
        int index = Math.floorMod(token, vector.length);
        vector[index] += 1f;
    }

    /**
     * 余弦相似度。向量已归一化时，点积就等于余弦，范围大约 [-1, 1]，越大越像。
     */
    static float cosine(float[] left, float[] right) {
        double dot = 0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
        }
        return (float) dot;
    }

    /** 把向量长度收成 1，这样不同长短的文本比较时主要看方向而不是模长。 */
    private static float[] l2Normalize(float[] vector) {
        double norm = 0;
        for (float value : vector) {
            norm += value * value;
        }
        if (norm == 0) {
            return vector;
        }
        float scale = (float) (1.0 / Math.sqrt(norm));
        float[] normalized = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalized[i] = vector[i] * scale;
        }
        return normalized;
    }
}
