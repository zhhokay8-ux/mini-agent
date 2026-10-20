package com.miniagent.web;

import com.miniagent.shop.ConfirmationService;
import com.miniagent.shop.ConfirmationService.ConfirmResult;
import com.miniagent.shop.OrderStore;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ConfirmController {

    private final ConfirmationService confirmations;
    private final OrderStore orderStore;

    public ConfirmController(ConfirmationService confirmations, OrderStore orderStore) {
        this.confirmations = confirmations;
        this.orderStore = orderStore;
    }

    /**
     * HITL 确认入口。approved=true 才 {@link OrderStore#submitRefund}；
     * 可用 {@code GET /api/refunds} 核对确认前后列表是否变化。
     */
    @PostMapping("/confirm")
    public Map<String, Object> confirm(@Valid @RequestBody ConfirmRequest request) {
        ConfirmResult result = confirmations.resolve(request.confirmationId(), request.approved());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("approved", result.approved());
        body.put("message", result.message());
        if (result.refund() != null) {
            body.put("refundId", result.refund().refundId());
            body.put("orderId", result.refund().orderId());
        }
        body.put("refundCount", orderStore.refunds().size());
        return body;
    }

    /** 验收用：确认前应为空或没有该订单。 */
    @GetMapping("/refunds")
    public Map<String, Object> refunds() {
        return Map.of("refunds", orderStore.refunds());
    }
}
