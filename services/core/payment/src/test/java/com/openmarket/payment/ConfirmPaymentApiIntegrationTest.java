package com.openmarket.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmarket.common.test.container.PostgresContainerConfiguration;
import com.openmarket.payment.command.application.PreparePaymentCommand;
import com.openmarket.payment.command.application.PreparePaymentUseCase;
import com.openmarket.payment.infra.PaymentReconcileJob;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 결제 승인(PAY-1, docs/architecture/payment.md "승인")과 재조회 작업. 토스 API는 WireMock({@link TossStub})이다. 승인
 * 읽기 타임아웃은 테스트를 빨리 돌리려고 1초로 줄이고, 재조회는 승인 요청 직후부터 대상이 되게 한다.
 */
@SpringBootTest(
        properties = {
            "spring.kafka.listener.auto-startup=false",
            "openmarket.payment.toss.read-timeout=1s",
            "openmarket.payment.reconcile-after=0s"
        })
@AutoConfigureMockMvc
@Import(PostgresContainerConfiguration.class)
class ConfirmPaymentApiIntegrationTest {

    static final Path EVENTS = Path.of("src/test/resources/events");

    @DynamicPropertySource
    static void toss(DynamicPropertyRegistry registry) {
        TossStub.register(registry);
    }

    @Autowired MockMvc mockMvc;

    @Autowired JdbcClient jdbc;

    @Autowired JsonMapper json;

    @Autowired PreparePaymentUseCase prepare;

    @Autowired PaymentReconcileJob reconcileJob;

    final UUID buyerId = UUID.randomUUID();
    final UUID orderId = UUID.randomUUID();
    final String paymentKey = "tgen_" + UUID.randomUUID().toString().replace("-", "");

    @BeforeEach
    void setUp() {
        TossStub.reset();
        prepare.execute(new PreparePaymentCommand(orderId, buyerId, 59_700, "반팔 티셔츠"));
    }

    ResultActions confirm(UUID buyer, String key, long amount, String idempotencyKey)
            throws Exception {
        return mockMvc.perform(
                post("/v1/payments/confirm")
                        .with(PaymentTokens.buyer(buyer))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"orderId\":\""
                                        + orderId
                                        + "\",\"paymentKey\":\""
                                        + key
                                        + "\",\"amount\":"
                                        + amount
                                        + "}"));
    }

    ResultActions confirm(String idempotencyKey) throws Exception {
        return confirm(buyerId, paymentKey, 59_700, idempotencyKey);
    }

    @Test
    @DisplayName("승인되면 200이고 APPROVED, PaymentApproved, 원장 한 줄이 남는다. 같은 키로 다시 보내면 첫 응답을 준다")
    void approves() throws Exception {
        TossStub.confirmReturns(200, TossStub.payment(paymentKey, orderId, 59_700, "DONE"));

        confirm("k-1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.amount").value(59_700))
                .andExpect(jsonPath("$.method").value("간편결제"));
        confirm("k-1")
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"));

        assertThat(paymentStatus()).isEqualTo("APPROVED");
        assertThat(ledger()).containsExactly("CONFIRM APPROVED DONE");
        assertThat(outboxTypes()).containsExactly("PaymentApproved");
        JsonNode approved = json.readTree(outboxPayload("PaymentApproved"));
        assertThat(approved.propertyNames()).isEqualTo(example("PaymentApproved").propertyNames());
        assertThat(approved.get("orderId").asString()).isEqualTo(orderId.toString());
        assertThat(approved.get("amount").asLong()).isEqualTo(59_700);
        assertThat(approved.get("method").asString()).isEqualTo("간편결제");
        assertThat(approved.get("approvedAt").asString()).isEqualTo("2026-10-20T06:01:20Z");
        assertThat(TossStub.SERVER.getAllServeEvents()).hasSize(1);
    }

    @Test
    @DisplayName("토스가 거절하면 422 PAYMENT_REJECTED이고 FAILED, PaymentFailed가 남는다. 새 결제 키로 다시 승인할 수 있다")
    void rejectsThenRetries() throws Exception {
        TossStub.confirmReturns(
                403, TossStub.error("REJECT_CARD_PAYMENT", "한도초과 혹은 잔액부족으로 결제에 실패했습니다."));

        confirm("k-1")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PAYMENT_REJECTED"))
                .andExpect(jsonPath("$.pgCode").value("REJECT_CARD_PAYMENT"))
                .andExpect(jsonPath("$.pgMessage").value("한도초과 혹은 잔액부족으로 결제에 실패했습니다."));
        assertThat(paymentStatus()).isEqualTo("FAILED");
        JsonNode failed = json.readTree(outboxPayload("PaymentFailed"));
        assertThat(failed.propertyNames()).isEqualTo(example("PaymentFailed").propertyNames());
        assertThat(failed.get("code").asString()).isEqualTo("REJECT_CARD_PAYMENT");

        String retryKey = paymentKey + "r";
        TossStub.confirmReturns(200, TossStub.payment(retryKey, orderId, 59_700, "DONE"));
        confirm(buyerId, retryKey, 59_700, "k-2").andExpect(status().isOk());
        assertThat(paymentStatus()).isEqualTo("APPROVED");
        assertThat(outboxTypes()).containsExactly("PaymentFailed", "PaymentApproved");
    }

    @Test
    @DisplayName("승인 응답이 타임아웃이면 조회로 정한다")
    void timeoutThenQuery() throws Exception {
        TossStub.confirmDelayed(1_500, TossStub.payment(paymentKey, orderId, 59_700, "DONE"));
        TossStub.findReturns(
                paymentKey, 200, TossStub.payment(paymentKey, orderId, 59_700, "DONE"));

        confirm("k-1").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));

        assertThat(ledger()).containsExactly("CONFIRM UNKNOWN TIMEOUT", "QUERY APPROVED DONE");
        assertThat(outboxTypes()).containsExactly("PaymentApproved");
    }

    @Test
    @DisplayName("조회로도 정하지 못하면 202 IN_PROGRESS이고, 재조회 작업이 나중에 정한다")
    void reconcilesLater() throws Exception {
        TossStub.confirmReturns(
                500, TossStub.error("FAILED_INTERNAL_SYSTEM_PROCESSING", "내부 시스템 처리 작업이 실패했습니다."));
        TossStub.findReturns(
                paymentKey,
                500,
                TossStub.error("FAILED_INTERNAL_SYSTEM_PROCESSING", "내부 시스템 처리 작업이 실패했습니다."));

        confirm("k-1")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        confirm("k-2").andExpect(status().isAccepted());
        assertThat(paymentStatus()).isEqualTo("IN_PROGRESS");
        assertThat(outboxTypes()).isEmpty();

        TossStub.findReturns(
                paymentKey, 200, TossStub.payment(paymentKey, orderId, 59_700, "DONE"));
        assertThat(reconcileJob.reconcile()).isEqualTo(1);
        assertThat(reconcileJob.reconcile()).isZero();

        assertThat(paymentStatus()).isEqualTo("APPROVED");
        assertThat(outboxTypes()).containsExactly("PaymentApproved");
        assertThat(ledger())
                .containsExactly(
                        "CONFIRM UNKNOWN FAILED_INTERNAL_SYSTEM_PROCESSING",
                        "QUERY UNKNOWN FAILED_INTERNAL_SYSTEM_PROCESSING",
                        "QUERY APPROVED DONE");
    }

    @Test
    @DisplayName("금액이 다르면 422, 다른 회원이면 404, 다른 결제 키로 다시 오면 409이고 토스를 부르지 않는다")
    void rejectsBeforeToss() throws Exception {
        confirm(buyerId, paymentKey, 1_000, "k-1")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PAYMENT_AMOUNT_MISMATCH"));
        confirm(UUID.randomUUID(), paymentKey, 59_700, "k-2")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
        assertThat(TossStub.SERVER.getAllServeEvents()).isEmpty();

        TossStub.confirmReturns(200, TossStub.payment(paymentKey, orderId, 59_700, "DONE"));
        confirm("k-3").andExpect(status().isOk());
        confirm(buyerId, paymentKey + "x", 59_700, "k-4")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_CONFIRMABLE"));
        assertThat(TossStub.SERVER.getAllServeEvents()).hasSize(1);
    }

    @Test
    @DisplayName("본문이 틀리면 400, Idempotency-Key가 없으면 400이다")
    void invalidInput() throws Exception {
        mockMvc.perform(
                        post("/v1/payments/confirm")
                                .with(PaymentTokens.buyer(buyerId))
                                .header("Idempotency-Key", "k-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"orderId\":\""
                                                + orderId
                                                + "\",\"paymentKey\":\"\",\"amount\":-1}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(
                        post("/v1/payments/confirm")
                                .with(PaymentTokens.buyer(buyerId))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_IDEMPOTENCY_KEY_REQUIRED"));
    }

    String paymentStatus() {
        return jdbc.sql("SELECT status FROM payment WHERE order_id = :orderId")
                .param("orderId", orderId)
                .query(String.class)
                .single();
    }

    List<String> ledger() {
        return jdbc.sql(
                        "SELECT l.kind || ' ' || l.outcome || ' ' || l.pg_code FROM payment_ledger_entry l"
                                + " JOIN payment p ON p.id = l.payment_id WHERE p.order_id = :orderId"
                                + " ORDER BY l.occurred_at, l.id")
                .param("orderId", orderId)
                .query(String.class)
                .list();
    }

    List<String> outboxTypes() {
        return jdbc.sql(
                        "SELECT type FROM outbox_event WHERE aggregate_id = :orderId ORDER BY"
                                + " occurred_at, id")
                .param("orderId", orderId.toString())
                .query(String.class)
                .list();
    }

    String outboxPayload(String type) {
        return jdbc.sql(
                        "SELECT payload::text FROM outbox_event WHERE aggregate_id = :orderId AND"
                                + " type = :type")
                .param("orderId", orderId.toString())
                .param("type", type)
                .query(String.class)
                .single();
    }

    JsonNode example(String type) throws Exception {
        return json.readTree(Files.readString(EVENTS.resolve(type + ".json")));
    }
}
