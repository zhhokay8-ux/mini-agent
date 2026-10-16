package com.miniagent.session;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 粗算即将送给模型的上下文体积。中文大约 1.5～2 字一个 token，这里用「字符数 / 2」打日志，
 * 用来确认 20 轮后请求体没有无限变长，不是计费精确值。
 */
@Component
public class TokenEstimator {

    /** 单条文本的大约 token 数。 */
    public int estimate(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, text.length() / 2);
    }

    /** 一组消息的大约 token 总数（只加 content）。 */
    public int estimateMessages(List<com.miniagent.chat.ChatMessage> messages) {
        int total = 0;
        for (com.miniagent.chat.ChatMessage message : messages) {
            total += estimate(message.content());
        }
        return total;
    }
}
