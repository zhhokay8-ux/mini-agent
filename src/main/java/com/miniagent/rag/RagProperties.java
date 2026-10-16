package com.miniagent.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 对应 {@code application.yml} 里 {@code rag.*}。
 *
 * @param chunkSize 每块最多多少字符；太大检索不准，太小丢失上下文
 * @param chunkOverlap 相邻两块重叠字符数，避免一句话被从中间切断后检索不到
 * @param topK 每次检索取相似度最高的几块塞进 Prompt
 * @param knowledgePattern Spring 资源通配符，默认扫 classpath 下 knowledge 目录
 */
@ConfigurationProperties(prefix = "rag")
public record RagProperties(int chunkSize, int chunkOverlap, int topK, String knowledgePattern) {

    /** 非法配置时回落到第 3 阶段默认值，避免启动后切块/检索行为怪异。 */
    public RagProperties {
        if (chunkSize <= 0) {
            chunkSize = 500;
        }
        if (chunkOverlap < 0 || chunkOverlap >= chunkSize) {
            chunkOverlap = Math.min(50, chunkSize / 10);
        }
        if (topK <= 0) {
            topK = 5;
        }
        if (knowledgePattern == null || knowledgePattern.isBlank()) {
            knowledgePattern = "classpath*:knowledge/**/*.{md,txt,pdf}";
        }
    }
}
