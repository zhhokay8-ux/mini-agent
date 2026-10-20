package com.miniagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.miniagent.shop.ConfirmationService;
import com.miniagent.shop.OrderRecord;
import com.miniagent.shop.OrderStore;
import com.miniagent.shop.PendingConfirmation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 写工具：只生成待确认单，不往 {@code OrderStore} 写退货。
 * 真正写入发生在用户调用 {@code POST /api/confirm} 且 approved=true 之后。
 */
@Component
public class CreateRefundTool implements AgentTool {

    private final ConfirmationService confirmations;
    private final OrderStore orderStore;

    public CreateRefundTool(ConfirmationService confirmations, OrderStore orderStore) {
        this.confirmations = confirmations;
        this.orderStore = orderStore;
    }

    @Override
    public String name() {
        return "create_refund";
    }

    @Override
    public String description() {
        return "为可退订单创建退货申请。本工具只会生成待确认单（need_confirm=true），"
                + "确认前不会写入退货库。用户说「帮我提交退货」时调用。"
                + "不可退或订单不存在时应先 get_order / search_knowledge，不要强行创建。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> orderId = new LinkedHashMap<>();
        orderId.put("type", "string");
        orderId.put("description", "要退货的订单号");
        return Map.of("orderId", orderId);
    }

    @Override
    public List<String> required() {
        return List.of("orderId");
    }

    /**
     * 校验订单可退后登记 HITL。返回里带 confirmationId，循环看到 need_confirm 会停下来等前端。
     */
    @Override
    public String execute(JsonNode arguments) {
        String orderId = arguments.path("orderId").asText("").trim();
        if (!StringUtils.hasText(orderId)) {
            return ToolJson.error("orderId is required");
        }
        OrderRecord order = orderStore.find(orderId).orElse(null);
        if (order == null) {
            return ToolJson.error("order not found: " + orderId);
        }
        if (!order.returnable()) {
            return ToolJson.error("order is not returnable: " + order.ineligibleReason());
        }
        if (orderStore.refunds().stream().anyMatch(refund -> refund.orderId().equals(order.id()))) {
            return ToolJson.error("refund already submitted for order " + order.id());
        }
        String preview = "订单 " + order.id() + "（" + order.item() + "，" + order.purchasedAt() + "购买，状态："
                + order.status() + "）申请 7 天无理由退货。确认后才会生成退货单。";
        PendingConfirmation pending = confirmations.registerRefund(order.id(), arguments.toString(), preview);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("need_confirm", true);
        body.put("confirmationId", pending.id());
        body.put("preview", pending.preview());
        body.put("orderId", order.id());
        return ToolJson.ok(body);
    }
}
