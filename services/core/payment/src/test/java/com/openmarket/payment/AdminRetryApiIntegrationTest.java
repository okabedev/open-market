package com.openmarket.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmarket.common.test.container.PostgresContainerConfiguration;
import com.openmarket.payment.command.domain.Payment;
import com.openmarket.payment.command.domain.PaymentRepository;
import com.openmarket.payment.command.domain.Refund;
import com.openmarket.payment.command.domain.RefundRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** 관리자 재시도 목록과 재시도(docs/architecture/payment.md "관리자 재시도"). 실패 상태는 도메인으로 만들어 저장한다. */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@AutoConfigureMockMvc
@Import(PostgresContainerConfiguration.class)
class AdminRetryApiIntegrationTest {

    static final Instant NOW = Instant.parse("2026-10-22T00:00:00Z");

    @DynamicPropertySource
    static void toss(DynamicPropertyRegistry registry) {
        TossStub.register(registry);
    }

    @Autowired MockMvc mockMvc;

    @Autowired PaymentRepository payments;

    @Autowired RefundRepository refunds;

    @Autowired TransactionTemplate transaction;

    @Autowired JdbcClient jdbc;

    @Autowired JsonMapper json;

    final UUID adminId = UUID.randomUUID();

    Payment approved() {
        Payment payment = Payment.ready(UUID.randomUUID(), UUID.randomUUID(), 59_700, "주문", NOW);
        payment.requestConfirm("tgen_" + UUID.randomUUID(), 59_700, NOW);
        payment.approve(1, "카드", NOW, NOW);
        return payment;
    }

    @Test
    @DisplayName("토스가 거절한 환불은 실패 목록에 나오고, 관리자가 다시 시도하면 REQUESTED·시도 2가 되고 감사 로그가 남는다")
    void retriesRefund() throws Exception {
        Payment payment = approved();
        Refund refund =
                Refund.requested(payment, UUID.randomUUID(), UUID.randomUUID(), 19_900, NOW);
        refund.fail("NOT_CANCELABLE_AMOUNT", NOW);
        transaction.executeWithoutResult(
                status -> {
                    payments.save(payment);
                    refunds.save(refund);
                });

        String list =
                mockMvc.perform(
                                get("/v1/admin/refunds/failed?size=100")
                                        .with(PaymentTokens.admin(adminId)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(json.readTree(list).get("items").valueStream())
                .anySatisfy(
                        item -> {
                            assertThat(item.get("refundId").asString())
                                    .isEqualTo(refund.id().toString());
                            assertThat(item.get("failureCode").asString())
                                    .isEqualTo("NOT_CANCELABLE_AMOUNT");
                            assertThat(item.get("attempt").asInt()).isEqualTo(1);
                        });
        mockMvc.perform(
                        post("/v1/admin/refunds/{id}/retry", refund.id())
                                .with(PaymentTokens.buyer(UUID.randomUUID())))
                .andExpect(status().isForbidden());

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(
                            post("/v1/admin/refunds/{id}/retry", refund.id())
                                    .with(PaymentTokens.admin(adminId)))
                    .andExpect(status().isNoContent());
        }

        assertThat(
                        jdbc.sql(
                                        "SELECT status || ' ' || attempt || ' ' ||"
                                                + " coalesce(failure_code, '-') FROM refund WHERE id ="
                                                + " :id")
                                .param("id", refund.id())
                                .query(String.class)
                                .single())
                .isEqualTo("REQUESTED 2 -");
        assertThat(audit("REFUND", refund.id()))
                .containsExactly("REFUND_RETRIED NOT_CANCELABLE_AMOUNT");
        mockMvc.perform(
                        post("/v1/admin/refunds/{id}/retry", UUID.randomUUID())
                                .with(PaymentTokens.admin(adminId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_REFUND_NOT_FOUND"));
    }

    @Test
    @DisplayName("토스가 거절한 보상 취소는 목록에 나오고, 다시 시도하면 실패 코드가 지워지고 시도 번호가 오른다")
    void retriesCancel() throws Exception {
        Payment payment = approved();
        payment.requestCancel(NOW);
        payment.cancelRejected("NOT_CANCELABLE_PAYMENT", NOW);
        transaction.executeWithoutResult(status -> payments.save(payment));

        String list =
                mockMvc.perform(
                                get("/v1/admin/payments/cancel-failures?size=100")
                                        .with(PaymentTokens.admin(adminId)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(json.readTree(list).get("items").valueStream())
                .anySatisfy(
                        item -> {
                            assertThat(item.get("paymentId").asString())
                                    .isEqualTo(payment.id().toString());
                            assertThat(item.get("cancelFailureCode").asString())
                                    .isEqualTo("NOT_CANCELABLE_PAYMENT");
                        });

        mockMvc.perform(
                        post("/v1/admin/payments/{id}/cancel-retry", payment.id())
                                .with(PaymentTokens.admin(adminId)))
                .andExpect(status().isNoContent());

        assertThat(
                        jdbc.sql(
                                        "SELECT coalesce(cancel_failure_code, '-') || ' ' ||"
                                                + " cancel_attempt FROM payment WHERE id = :id")
                                .param("id", payment.id())
                                .query(String.class)
                                .single())
                .isEqualTo("- 2");
        assertThat(audit("PAYMENT", payment.id()))
                .containsExactly("PAYMENT_CANCEL_RETRIED NOT_CANCELABLE_PAYMENT");

        Payment untouched = approved();
        transaction.executeWithoutResult(status -> payments.save(untouched));
        mockMvc.perform(
                        post("/v1/admin/payments/{id}/cancel-retry", untouched.id())
                                .with(PaymentTokens.admin(adminId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_RETRYABLE"));
    }

    private List<String> audit(String targetType, UUID targetId) {
        return jdbc.sql(
                        "SELECT action || ' ' || detail FROM audit_log WHERE target_type = :type"
                                + " AND target_id = :id")
                .param("type", targetType)
                .param("id", targetId.toString())
                .query(String.class)
                .list();
    }
}
