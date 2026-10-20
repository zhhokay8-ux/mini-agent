package com.miniagent.shop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniagent.tool.CreateRefundTool;
import com.miniagent.tool.GetOrderTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConfirmationServiceTest {

    private final OrderStore store = new OrderStore();
    private final ConfirmationService confirmations = new ConfirmationService(store);
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void reset() {
        store.clearRefunds();
    }

    @Test
    void getOrder1001IsReturnable() throws Exception {
        String json = new GetOrderTool(store).execute(mapper.readTree("{\"orderId\":\"1001\"}"));
        assertThat(json).contains("\"returnable\":true").contains("无线耳机");
    }

    @Test
    void createRefundDoesNotWriteUntilApproved() throws Exception {
        CreateRefundTool tool = new CreateRefundTool(confirmations, store);
        String json = tool.execute(mapper.readTree("{\"orderId\":\"1001\"}"));
        assertThat(json).contains("\"need_confirm\":true");
        assertThat(store.refunds()).isEmpty();

        String confirmationId = mapper.readTree(json).path("confirmationId").asText();
        ConfirmationService.ConfirmResult result = confirmations.resolve(confirmationId, true);
        assertThat(result.approved()).isTrue();
        assertThat(store.refunds()).hasSize(1);
        assertThat(store.refunds().get(0).orderId()).isEqualTo("1001");
    }

    @Test
    void rejectDoesNotCreateRefund() throws Exception {
        CreateRefundTool tool = new CreateRefundTool(confirmations, store);
        String json = tool.execute(mapper.readTree("{\"orderId\":\"1001\"}"));
        String confirmationId = mapper.readTree(json).path("confirmationId").asText();
        confirmations.resolve(confirmationId, false);
        assertThat(store.refunds()).isEmpty();
    }

    @Test
    void cannotRefundIneligibleOrder() throws Exception {
        String json = new CreateRefundTool(confirmations, store).execute(mapper.readTree("{\"orderId\":\"1002\"}"));
        assertThat(json).contains("\"ok\":false");
        assertThat(store.refunds()).isEmpty();
    }

    @Test
    void unknownConfirmationFails() {
        assertThatThrownBy(() -> confirmations.resolve("missing", true))
                .isInstanceOf(ConfirmationNotFoundException.class);
    }
}
