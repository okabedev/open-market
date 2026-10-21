package com.openmarket.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmarket.common.test.container.PostgresContainerConfiguration;
import com.openmarket.payment.command.application.PreparePaymentCommand;
import com.openmarket.payment.command.application.PreparePaymentUseCase;
import com.openmarket.payment.command.application.RequestPaymentCancelCommand;
import com.openmarket.payment.command.application.RequestPaymentCancelUseCase;
import com.openmarket.payment.infra.PaymentCancelJob;
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
 * 결제 전 취소된 주문의 결제 무효화와 보상 취소(PAY-3, docs/architecture/payment.md "보상 취소"). 토스 API는 WireMock({@link
 * TossStub})이다. 설정은 {@link ConfirmPaymentApiIntegrationTest}와 같아 컨텍스트를 함께 쓴다.
 */
@SpringBootTest(
        properties = {
            "spring.kafka.listener.auto-startup=false",
            "openmarket.payment.toss.read-timeout=1s",
            "openmarket.payment.reconcile-after=0s"
        })
@AutoConfigureMockMvc
@Import(PostgresContainerConfiguration.class)
class PaymentCancelIntegrationTest {

    static final Path EVENTS = Path.of("src/test/resources/events");

    @DynamicPropertySource
    static void toss(DynamicPropertyRegistry registry) {
        TossStub.register(registry);
    }

    @Autowired MockMvc mockMvc;

    @Autowired JdbcClient jdbc;

    @Autowired JsonMapper json;

    @Autowired PreparePaymentUseCase prepare;

    @Autowired RequestPaymentCancelUseCase requestCancel;

    @Autowired PaymentReconcileJob reconcileJob;

    @Autowired PaymentCancelJob cancelJob;

    final UUID buyerId = UUID.randomUUID();
    final UUID orderId = UUID.randomUUID();
    final String paymentKey = "tgen_" + UUID.randomUUID().toString().replace("-", "");

    @BeforeEach
    void setUp() {
        TossStub.reset();
        prepare.execute(new PreparePaymentCommand(orderId, buyerId, 59_700, "반팔 티셔츠"));
    }

    ResultActions confirm(String idempotencyKey) throws Exception {
        return mockMvc.perform(
                post("/v1/payments/confirm")
                        .with(PaymentTokens.buyer(buyerId))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"orderId\":\""
                                        + orderId
                                        + "\",\"paymentKey\":\""
                                        + paymentKey
                                        + "\",\"amount\":59700}"));
    }

    void orderCancelled() {
        requestCancel.execute(new RequestPaymentCancelCommand(orderId));
    }

    void stubCancel() {
        TossStub.cancelReturns(
                paymentKey,
                200,
                TossStub.cancelledPayment(paymentKey, orderId, 59_700, "CANCELED", 0));
    }

    @Test
    @DisplayName("주문이 먼저 취소되면 READY 결제는 CANCELLED이고, 승인 요청은 토스를 부르지 않고 409 PAYMENT_CANCELLED다")
    void cancelsBeforeConfirm() throws Exception {
        orderCancelled();

        confirm("k-1")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_CANCELLED"));

        assertThat(paymentStatus()).isEqualTo("CANCELLED");
        assertThat(TossStub.SERVER.getAllServeEvents()).isEmpty();
        assertThat(outboxTypes()).isEmpty();
        assertThat(cancelJob.cancel()).isZero();
    }

    @Test
    @DisplayName(
            "승인된 결제의 주문이 취소되면 취소 작업이 토스 전액 취소를 부르고 CANCELLED와 PaymentCancelled를 남긴다. 다시 돌아도 한 번이다")
    void compensatesApproved() throws Exception {
        TossStub.confirmReturns(200, TossStub.payment(paymentKey, orderId, 59_700, "DONE"));
        confirm("k-1").andExpect(status().isOk());
        orderCancelled();
        stubCancel();

        assertThat(cancelJob.cancel()).isEqualTo(1);
        assertThat(cancelJob.cancel()).isZero();

        assertThat(paymentStatus()).isEqualTo("CANCELLED");
        assertThat(ledger()).containsExactly("CONFIRM APPROVED DONE", "CANCEL CANCELLED CANCELED");
        assertThat(outboxTypes()).containsExactly("PaymentApproved", "PaymentCancelled");
        JsonNode cancelled = json.readTree(outboxPayload("PaymentCancelled"));
        assertThat(cancelled.propertyNames())
                .isEqualTo(example("PaymentCancelled").propertyNames());
        assertThat(cancelled.get("amount").asLong()).isEqualTo(59_700);
        assertThat(cancelled.get("reason").asString()).isEqualTo("ORDER_CANCELLED");
        assertThat(
                        TossStub.SERVER.getAllServeEvents().stream()
                                .filter(e -> e.getRequest().getUrl().endsWith("/cancel"))
                                .map(e -> e.getRequest().getHeader("Idempotency-Key"))
                                .toList())
                .singleElement()
                .asString()
                .endsWith("-cancel");
    }

    @Test
    @DisplayName("승인 결과를 기다리는 동안 주문이 취소되고 재조회로 승인이 확인되면 보상 취소한다. 같은 결제 키로 다시 승인하면 409다")
    void compensatesLateApproval() throws Exception {
        TossStub.confirmReturns(
                500, TossStub.error("FAILED_INTERNAL_SYSTEM_PROCESSING", "내부 시스템 처리 작업이 실패했습니다."));
        TossStub.findReturns(
                paymentKey,
                500,
                TossStub.error("FAILED_INTERNAL_SYSTEM_PROCESSING", "내부 시스템 처리 작업이 실패했습니다."));
        confirm("k-1").andExpect(status().isAccepted());

        orderCancelled();
        confirm("k-2")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_CANCELLED"));
        assertThat(cancelJob.cancel()).isZero();

        TossStub.findReturns(
                paymentKey, 200, TossStub.payment(paymentKey, orderId, 59_700, "DONE"));
        reconcileJob.reconcile();
        assertThat(paymentStatus()).isEqualTo("APPROVED");

        stubCancel();
        assertThat(cancelJob.cancel()).isEqualTo(1);
        assertThat(paymentStatus()).isEqualTo("CANCELLED");
        assertThat(outboxTypes()).containsExactly("PaymentApproved", "PaymentCancelled");
    }

    @Test
    @DisplayName("토스 취소 응답이 타임아웃이면 APPROVED로 두고 다음 주기에 다시 취소한다. 거절되면 더는 부르지 않는다")
    void retriesAndStops() throws Exception {
        TossStub.confirmReturns(200, TossStub.payment(paymentKey, orderId, 59_700, "DONE"));
        confirm("k-1").andExpect(status().isOk());
        orderCancelled();

        TossStub.cancelDelayed(
                paymentKey,
                1_500,
                TossStub.cancelledPayment(paymentKey, orderId, 59_700, "CANCELED", 0));
        assertThat(cancelJob.cancel()).isEqualTo(1);
        assertThat(paymentStatus()).isEqualTo("APPROVED");

        TossStub.cancelReturns(
                paymentKey, 403, TossStub.error("NOT_CANCELABLE_PAYMENT", "취소 할 수 없는 결제 입니다."));
        assertThat(cancelJob.cancel()).isEqualTo(1);
        assertThat(cancelJob.cancel()).isZero();

        assertThat(paymentStatus()).isEqualTo("APPROVED");
        assertThat(
                        jdbc.sql(
                                        "SELECT cancel_failure_code FROM payment WHERE order_id = :orderId")
                                .param("orderId", orderId)
                                .query(String.class)
                                .single())
                .isEqualTo("NOT_CANCELABLE_PAYMENT");
        assertThat(ledger())
                .containsExactly(
                        "CONFIRM APPROVED DONE",
                        "CANCEL UNKNOWN TIMEOUT",
                        "CANCEL REJECTED NOT_CANCELABLE_PAYMENT");
        assertThat(outboxTypes()).containsExactly("PaymentApproved");
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
