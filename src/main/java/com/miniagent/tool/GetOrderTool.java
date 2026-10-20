package com.miniagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.miniagent.shop.OrderRecord;
import com.miniagent.shop.OrderStore;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 查模拟订单。问「订单 1001 能退吗」时应先调本工具，再 search_knowledge 对政策。 */
@Component
public class GetOrderTool implements AgentTool {

    private final OrderStore orderStore;

    public GetOrderTool(OrderStore orderStore) {
        this.orderStore = orderStore;
    }

    @Override
    public String name() {
        return "get_order";
    }

    @Override
    public String description() {
        return "按订单号查询模拟商城订单（商品、签收状态、是否仍可退）。"
                + "问某笔订单能不能退、买的是什么时使用。示例订单：1001 上周耳机可退，1002 超窗不可退。"
                + "不查询制度条文，制度请用 search_knowledge。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> orderId = new LinkedHashMap<>();
        orderId.put("type", "string");
        orderId.put("description", "订单号，例如 1001");
        return Map.of("orderId", orderId);
    }

    @Override
    public List<String> required() {
        return List.of("orderId");
    }

    /** 只读查询，不会创建退货单。 */
    @Override
    public String execute(JsonNode arguments) {
        String orderId = arguments.path("orderId").asText("").trim();
        if (!StringUtils.hasText(orderId)) {
            return ToolJson.error("orderId is required");
        }
        return orderStore
                .find(orderId)
                .map(this::toJson)
                .orElseGet(() -> ToolJson.error("order not found: " + orderId));
    }

    private String toJson(OrderRecord order) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderId", order.id());
        body.put("item", order.item());
        body.put("status", order.status());
        body.put("purchasedAt", order.purchasedAt());
        body.put("returnable", order.returnable());
        if (order.ineligibleReason() != null) {
            body.put("ineligibleReason", order.ineligibleReason());
        }
        return ToolJson.ok(body);
    }
}
