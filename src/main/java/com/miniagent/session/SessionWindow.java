package com.miniagent.session;

import java.util.List;

/** 纯函数：从按时间排好的消息里切出「最近 N 轮」和「需要压进摘要的旧消息」。 */
public final class SessionWindow {

    private SessionWindow() {}

    /**
     * 最近 {@code maxMessages} 条（通常 = 轮数 × 2）。不足则返回全部。
     */
    public static List<SessionMessageEntity> recent(List<SessionMessageEntity> ordered, int maxMessages) {
        if (ordered.size() <= maxMessages) {
            return List.copyOf(ordered);
        }
        return List.copyOf(ordered.subList(ordered.size() - maxMessages, ordered.size()));
    }

    /**
     * 窗口之外、且还没 compact 过的旧消息，用来生成/更新摘要。
     */
    public static List<SessionMessageEntity> staleUncompacted(List<SessionMessageEntity> ordered, int maxMessages) {
        if (ordered.size() <= maxMessages) {
            return List.of();
        }
        return ordered.subList(0, ordered.size() - maxMessages).stream()
                .filter(message -> !message.isCompacted())
                .toList();
    }

    /**
     * 追问识别：短句里带「那 / 这个 / 刚才」或明确「怎么申请」时，检索要拼上上一轮用户问题。
     * 否则「怎么请假」会被误当成报销追问。
     */
    public static boolean isFollowUp(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        return question.contains("那")
                || question.contains("刚才")
                || question.contains("上面")
                || question.contains("这个")
                || question.contains("怎么申请")
                || question.contains("如何申请");
    }
}
