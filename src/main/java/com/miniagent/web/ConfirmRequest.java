package com.miniagent.web;

import jakarta.validation.constraints.NotBlank;

/**
 * 用户对写操作的确认。
 *
 * @param confirmationId {@link com.miniagent.shop.PendingConfirmation#id()}
 * @param approved true 才真正创建退货单；false 只取消待办
 */
public record ConfirmRequest(@NotBlank String confirmationId, boolean approved) {}
