package com.miniagent.shop;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;

/**
 * 内存订单/退货库。1001 是「上周买的耳机」（可退），1002 超窗不可退。
 * {@link #submitRefund} 只应被确认通过后的服务调用，工具本身不能直接写这里。
 */
@Component
public class OrderStore {

    private final Map<String, OrderRecord> orders = new ConcurrentHashMap<>();
    private final List<RefundRecord> refunds = new CopyOnWriteArrayList<>();

    public OrderStore() {
        orders.put(
                "1001",
                new OrderRecord("1001", "无线耳机", "已签收", "上周", true, null));
        orders.put(
                "1002",
                new OrderRecord("1002", "无线耳机", "已签收", "40 天前", false, "超过 7 天无理由退货窗口"));
    }

    /** 按订单号查找；没有则 empty，由工具返回错误而不是抛 500。 */
    public Optional<OrderRecord> find(String orderId) {
        if (orderId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(orders.get(orderId.trim()));
    }

    /** 用户确认后才创建退货单。同一订单重复提交会失败。 */
    public synchronized RefundRecord submitRefund(String orderId) {
        OrderRecord order = find(orderId).orElseThrow(() -> new IllegalArgumentException("order not found: " + orderId));
        if (!order.returnable()) {
            throw new IllegalStateException(order.ineligibleReason());
        }
        boolean already = refunds.stream().anyMatch(refund -> refund.orderId().equals(order.id()));
        if (already) {
            throw new IllegalStateException("refund already submitted for order " + order.id());
        }
        RefundRecord refund = new RefundRecord("R-" + UUID.randomUUID().toString().substring(0, 8), order.id(), order.item(), Instant.now());
        refunds.add(refund);
        return refund;
    }

    /** 验收用：确认前必须为空，确认后应有对应订单的退货单。 */
    public List<RefundRecord> refunds() {
        return List.copyOf(refunds);
    }

    /** 测试里清空退货单，避免用例互相污染。 */
    public void clearRefunds() {
        refunds.clear();
    }

    public List<OrderRecord> orders() {
        return new ArrayList<>(orders.values());
    }
}
