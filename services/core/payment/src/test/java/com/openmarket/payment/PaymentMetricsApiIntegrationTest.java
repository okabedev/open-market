package com.openmarket.payment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmarket.common.test.container.PostgresContainerConfiguration;
import java.sql.Timestamp;
import java.time.Instant;
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

/**
 * 관리자 결제 지표(ADM-4, docs/architecture/payment.md "운영 지표"). 원장의 승인 시도(CONFIRM)를 KST 날짜별로 센다. 결과가 정해지지
 * 않은 시도는 같은 결제 키의 뒤 조회(QUERY)에서 정해진 첫 결과를 쓴다. 다른 테스트와 겹치지 않게 2025년 3월 날짜를 쓴다.
 */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@AutoConfigureMockMvc
@Import(PostgresContainerConfiguration.class)
class PaymentMetricsApiIntegrationTest {

    @DynamicPropertySource
    static void toss(DynamicPropertyRegistry registry) {
        TossStub.register(registry);
    }

    @Autowired MockMvc mockMvc;
    @Autowired JdbcClient jdbc;

    final UUID adminId = UUID.randomUUID();

    @Test
    @DisplayName("날짜(KST)별 승인·거절·미정 수와 실패율(거절 ÷ (승인 + 거절))을 주고, 시도가 없는 날은 0과 빈 실패율이다")
    void countsConfirmAttemptsByKstDay() throws Exception {
        // 2025-03-03 KST = 2025-03-02T15:00Z ~ 2025-03-03T15:00Z
        attempt("APPROVED", "2025-03-02T15:30:00Z"); // 3일 00:30 KST
        attempt("REJECTED", "2025-03-03T02:00:00Z");
        attempt("REJECTED", "2025-03-03T03:00:00Z");
        // 4일: 응답을 못 받았지만 뒤 조회로 승인이 정해짐 / 끝까지 정해지지 않음
        UUID unknown = attempt("UNKNOWN", "2025-03-04T01:00:00Z");
        query(unknown, "PENDING", "2025-03-04T01:01:00Z");
        query(unknown, "APPROVED", "2025-03-04T01:02:00Z");
        query(unknown, "REJECTED", "2025-03-04T01:03:00Z"); // 첫 결과가 아니다
        UUID pending = attempt("PENDING", "2025-03-04T02:00:00Z");
        query(pending, "PENDING", "2025-03-04T02:01:00Z");
        attempt("APPROVED", "2025-03-02T14:59:00Z"); // 2일 23:59 KST, 기간 밖

        mockMvc.perform(
                        get("/v1/admin/metrics/payments")
                                .param("from", "2025-03-03")
                                .param("to", "2025-03-05")
                                .with(PaymentTokens.admin(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days.length()").value(3))
                .andExpect(jsonPath("$.days[0].date").value("2025-03-03"))
                .andExpect(jsonPath("$.days[0].approvedCount").value(1))
                .andExpect(jsonPath("$.days[0].rejectedCount").value(2))
                .andExpect(jsonPath("$.days[0].undecidedCount").value(0))
                .andExpect(jsonPath("$.days[0].failureRate").value(0.6667))
                .andExpect(jsonPath("$.days[1].date").value("2025-03-04"))
                .andExpect(jsonPath("$.days[1].approvedCount").value(1))
                .andExpect(jsonPath("$.days[1].rejectedCount").value(0))
                .andExpect(jsonPath("$.days[1].undecidedCount").value(1))
                .andExpect(jsonPath("$.days[1].failureRate").value(0.0))
                .andExpect(jsonPath("$.days[2].approvedCount").value(0))
                .andExpect(jsonPath("$.days[2].failureRate").doesNotExist());
    }

    @Test
    @DisplayName(
            "기간이 31일을 넘으면 400 PAYMENT_METRICS_PERIOD_TOO_LONG, 거꾸로면 400 PAYMENT_INVALID_INPUT, 관리자가 아니면 403")
    void validatesPeriodAndRole() throws Exception {
        mockMvc.perform(
                        get("/v1/admin/metrics/payments")
                                .param("from", "2025-03-01")
                                .param("to", "2025-04-01")
                                .with(PaymentTokens.admin(adminId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT_METRICS_PERIOD_TOO_LONG"));
        mockMvc.perform(
                        get("/v1/admin/metrics/payments")
                                .param("from", "2025-03-05")
                                .param("to", "2025-03-04")
                                .with(PaymentTokens.admin(adminId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT_INVALID_INPUT"));
        mockMvc.perform(
                        get("/v1/admin/metrics/payments")
                                .param("from", "2025-03-01")
                                .param("to", "2025-03-02")
                                .with(PaymentTokens.buyer(UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    /** 결제 하나와 그 승인 시도 한 줄. 결제 ID를 돌려준다. 결제 키는 결제 ID로 만든다. */
    private UUID attempt(String outcome, String at) {
        UUID paymentId = UUID.randomUUID();
        Timestamp time = Timestamp.from(Instant.parse(at));
        jdbc.sql(
                        "INSERT INTO payment (id, order_id, buyer_id, amount, order_name, status, created_at,"
                                + " updated_at, version) VALUES (:id, :order, :buyer, 10000, '지표 주문',"
                                + " 'IN_PROGRESS', :at, :at, 0)")
                .param("id", paymentId)
                .param("order", UUID.randomUUID())
                .param("buyer", UUID.randomUUID())
                .param("at", time)
                .update();
        ledger(paymentId, "CONFIRM", outcome, time);
        return paymentId;
    }

    private void query(UUID paymentId, String outcome, String at) {
        ledger(paymentId, "QUERY", outcome, Timestamp.from(Instant.parse(at)));
    }

    private void ledger(UUID paymentId, String kind, String outcome, Timestamp at) {
        jdbc.sql(
                        "INSERT INTO payment_ledger_entry (id, payment_id, kind, outcome, payment_key,"
                                + " occurred_at) VALUES (:id, :payment, :kind, :outcome, :key, :at)")
                .param("id", UUID.randomUUID())
                .param("payment", paymentId)
                .param("kind", kind)
                .param("outcome", outcome)
                .param("key", "tgen_" + paymentId)
                .param("at", at)
                .update();
    }
}
