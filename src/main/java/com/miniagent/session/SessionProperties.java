package com.miniagent.session;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 对应 {@code session.*}。
 *
 * @param maxRecentTurns 送给模型的最近完整轮数（一轮 = 用户 + 助手各一条）
 * @param summaryMaxChars 压缩旧对话时摘要长度上限，避免摘要本身又把窗口撑爆
 */
@ConfigurationProperties(prefix = "session")
public record SessionProperties(int maxRecentTurns, int summaryMaxChars) {

    public SessionProperties {
        if (maxRecentTurns <= 0) {
            maxRecentTurns = 6;
        }
        if (summaryMaxChars <= 0) {
            summaryMaxChars = 400;
        }
    }

    /** 窗口内最多保留的消息条数（每轮 2 条）。 */
    public int maxRecentMessages() {
        return maxRecentTurns * 2;
    }
}
