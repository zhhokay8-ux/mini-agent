package com.miniagent.shop;

/**
 * 模拟订单。第 5 阶段用来练「先查单再对政策」。
 *
 * @param returnable 是否仍在退货窗口且状态允许退
 * @param ineligibleReason 不可退时的原因，可退则为 null
 */
public record OrderRecord(
        String id,
        String item,
        String status,
        String purchasedAt,
        boolean returnable,
        String ineligibleReason) {}
