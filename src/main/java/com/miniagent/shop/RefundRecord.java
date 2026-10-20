package com.miniagent.shop;

import java.time.Instant;

/** 已确认后才存在的退货单。确认前这里必须是空的。 */
public record RefundRecord(String refundId, String orderId, String item, Instant createdAt) {}
