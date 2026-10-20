package com.miniagent.shop;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * 人工确认（HITL）：写工具只往这里登记，不改订单。
 * 用户 {@code approved=true} 才调用 {@link OrderStore#submitRefund}。
 */
@Service
public class ConfirmationService {

    private final OrderStore orderStore;
    private final Map<String, PendingConfirmation> pending = new ConcurrentHashMap<>();

    public ConfirmationService(OrderStore orderStore) {
        this.orderStore = orderStore;
    }

    /**
     * 登记一笔待确认退货。此时 {@link OrderStore#refunds()} 仍不应出现该订单。
     */
    public PendingConfirmation registerRefund(String orderId, String argumentsJson, String preview) {
        String id = UUID.randomUUID().toString();
        PendingConfirmation confirmation =
                new PendingConfirmation(id, "create_refund", argumentsJson, preview, orderId);
        pending.put(id, confirmation);
        return confirmation;
    }

    public Optional<PendingConfirmation> find(String confirmationId) {
        if (confirmationId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(pending.get(confirmationId));
    }

    /**
     * 用户点确认或取消。
     *
     * @return 通过时带上新建的 {@link RefundRecord}；拒绝时 refund 为 null
     */
    public ConfirmResult resolve(String confirmationId, boolean approved) {
        PendingConfirmation confirmation = pending.remove(confirmationId);
        if (confirmation == null) {
            throw new ConfirmationNotFoundException(confirmationId);
        }
        if (!approved) {
            return new ConfirmResult(false, confirmation, null, "用户取消，未创建退货单");
        }
        RefundRecord refund = orderStore.submitRefund(confirmation.orderId());
        return new ConfirmResult(true, confirmation, refund, "已创建退货单 " + refund.refundId());
    }

    public record ConfirmResult(
            boolean approved, PendingConfirmation confirmation, RefundRecord refund, String message) {}
}
