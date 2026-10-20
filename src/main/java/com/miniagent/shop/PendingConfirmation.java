package com.miniagent.shop;

/**
 * 写操作的待确认单。发给前端展示，确认前 {@link OrderStore} 里还没有退货单。
 *
 * @param id 确认接口要带的 confirmationId
 * @param tool 哪个写工具触发的，例如 create_refund
 * @param arguments 模型当时填的参数 JSON，方便核对
 * @param preview 给用户看的中文摘要
 * @param orderId 确认通过后真正去退的订单号
 */
public record PendingConfirmation(String id, String tool, String arguments, String preview, String orderId) {}
