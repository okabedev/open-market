package com.openmarket.payment.infra.toss;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.openmarket.payment.TossStub;
import com.openmarket.payment.command.application.PaymentGatewayPort.Approved;
import com.openmarket.payment.command.application.PaymentGatewayPort.CancelRejected;
import com.openmarket.payment.command.application.PaymentGatewayPort.Cancelled;
import com.openmarket.payment.command.application.PaymentGatewayPort.Pending;
import com.openmarket.payment.command.application.PaymentGatewayPort.Rejected;
import com.openmarket.payment.command.application.PaymentGatewayPort.Result;
import com.openmarket.payment.command.application.PaymentGatewayPort.Unknown;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.web.client.RestClient;

/**
 * 토스 응답의 해석(docs/architecture/payment.md "승인", "조회 결과", "보상 취소"). 토스 API는 WireMock({@link
 * TossStub})이다.
 */
class TossPaymentGatewayAdapterTest {

    static final String SECRET = "test_gsk_test";

    final UUID orderId = UUID.randomUUID();
    final String paymentKey = "tgen_" + UUID.randomUUID().toString().replace("-", "");

    TossPaymentGatewayAdapter adapter = adapter(Duration.ofSeconds(2), Duration.ofSeconds(1));

    static TossPaymentGatewayAdapter adapter(Duration connect, Duration read) {
        return new TossPaymentGatewayAdapter(
                RestClient.builder(),
                new TossProperties(URI.create(TossStub.SERVER.baseUrl()), SECRET, connect, read));
    }

    @BeforeEach
    void setUp() {
        TossStub.reset();
    }

    @Test
    @DisplayName("승인 요청은 시크릿 키 Basic 인증과 토스 Idempotency-Key를 붙이고, 200이면 승인 결과를 읽는다")
    void confirms() {
        TossStub.confirmReturns(200, TossStub.payment(paymentKey, orderId, 59_700, "DONE"));

        Result result = adapter.confirm(paymentKey, orderId, 59_700, "pay-1-1");

        assertThat(result)
                .isEqualTo(
                        new Approved(
                                "DONE",
                                59_700,
                                "간편결제",
                                Instant.parse("2026-10-20T06:01:20Z"),
                                "txrd_a01k8ztest0000000000000000"));
        String basic =
                Base64.getEncoder().encodeToString((SECRET + ":").getBytes(StandardCharsets.UTF_8));
        TossStub.SERVER.verify(
                postRequestedFor(urlPathEqualTo(TossStub.CONFIRM))
                        .withHeader("Authorization", equalTo("Basic " + basic))
                        .withHeader("Idempotency-Key", equalTo("pay-1-1"))
                        .withRequestBody(
                                equalToJson(
                                        "{\"paymentKey\":\""
                                                + paymentKey
                                                + "\",\"orderId\":\""
                                                + orderId
                                                + "\",\"amount\":59700}")));
    }

    @Test
    @DisplayName("카드 거절 같은 4xx는 거절이다")
    void rejects() {
        TossStub.confirmReturns(
                403, TossStub.error("REJECT_CARD_PAYMENT", "한도초과 혹은 잔액부족으로 결제에 실패했습니다."));

        assertThat(adapter.confirm(paymentKey, orderId, 59_700, "k"))
                .isEqualTo(new Rejected("REJECT_CARD_PAYMENT", "한도초과 혹은 잔액부족으로 결제에 실패했습니다."));
    }

    @ParameterizedTest
    @CsvSource({
        "400, ALREADY_PROCESSED_PAYMENT",
        "409, IDEMPOTENT_REQUEST_PROCESSING",
        "500, FAILED_INTERNAL_SYSTEM_PROCESSING"
    })
    @DisplayName("토스가 이미 처리했을 수 있는 승인 응답은 실패로 보지 않는다")
    void unknownConfirm(int status, String code) {
        TossStub.confirmReturns(status, TossStub.error(code, "메시지"));

        assertThat(adapter.confirm(paymentKey, orderId, 59_700, "k")).isEqualTo(new Unknown(code));
    }

    @Test
    @DisplayName("응답이 읽기 타임아웃보다 늦으면 TIMEOUT, 연결할 수 없으면 CONNECTION_FAILED다")
    void ioFailures() {
        TossStub.confirmDelayed(1_500, TossStub.payment(paymentKey, orderId, 59_700, "DONE"));

        assertThat(adapter.confirm(paymentKey, orderId, 59_700, "k"))
                .isEqualTo(new Unknown("TIMEOUT"));

        TossPaymentGatewayAdapter closed =
                new TossPaymentGatewayAdapter(
                        RestClient.builder(),
                        new TossProperties(
                                URI.create("http://localhost:1"),
                                SECRET,
                                Duration.ofSeconds(1),
                                Duration.ofSeconds(1)));
        assertThat(closed.confirm(paymentKey, orderId, 59_700, "k"))
                .isEqualTo(new Unknown("CONNECTION_FAILED"));
    }

    @ParameterizedTest
    @CsvSource({"DONE", "CANCELED", "PARTIAL_CANCELED"})
    @DisplayName("조회한 결제가 승인 뒤의 상태면 승인이다")
    void findApproved(String status) {
        TossStub.findReturns(
                paymentKey, 200, TossStub.payment(paymentKey, orderId, 59_700, status));

        assertThat(adapter.find(paymentKey)).isInstanceOf(Approved.class);
    }

    @Test
    @DisplayName("조회한 결제가 중단·만료면 거절, 진행 중이면 미정, 토스에 없으면(404) 거절, 그 밖의 오류는 알 수 없음이다")
    void findOthers() {
        TossStub.findReturns(
                paymentKey, 200, TossStub.payment(paymentKey, orderId, 59_700, "EXPIRED"));
        assertThat(adapter.find(paymentKey)).isEqualTo(new Rejected("EXPIRED", "결제가 완료되지 않았습니다."));

        TossStub.findReturns(
                paymentKey, 200, TossStub.payment(paymentKey, orderId, 59_700, "IN_PROGRESS"));
        assertThat(adapter.find(paymentKey)).isEqualTo(new Pending("IN_PROGRESS"));

        TossStub.findReturns(
                paymentKey, 404, TossStub.error("NOT_FOUND_PAYMENT", "존재하지 않는 결제 정보 입니다."));
        assertThat(adapter.find(paymentKey))
                .isEqualTo(new Rejected("NOT_FOUND_PAYMENT", "존재하지 않는 결제 정보 입니다."));

        TossStub.findReturns(
                paymentKey,
                401,
                TossStub.error("UNAUTHORIZED_KEY", "인증되지 않은 시크릿 키 혹은 클라이언트 키 입니다."));
        assertThat(adapter.find(paymentKey)).isEqualTo(new Unknown("UNAUTHORIZED_KEY"));
    }

    @Test
    @DisplayName("취소 요청은 결제 키 경로에 취소 사유와 토스 Idempotency-Key를 붙이고, 남은 금액이 0이면 취소다")
    void cancels() {
        TossStub.cancelReturns(
                paymentKey,
                200,
                TossStub.cancelledPayment(paymentKey, orderId, 59_700, "CANCELED", 0));

        assertThat(adapter.cancel(paymentKey, "주문 취소", "pay-1-cancel"))
                .isEqualTo(new Cancelled("CANCELED", "txrd_a01k8zcancel00000000000000"));
        TossStub.SERVER.verify(
                postRequestedFor(urlPathEqualTo(TossStub.cancelPath(paymentKey)))
                        .withHeader("Idempotency-Key", equalTo("pay-1-cancel"))
                        .withRequestBody(equalToJson("{\"cancelReason\":\"주문 취소\"}")));
    }

    @Test
    @DisplayName("이미 취소된 결제면 취소로 본다. 남은 금액이 있으면 전액 취소가 아니라 거절이다")
    void cancelEdgeCases() {
        TossStub.cancelReturns(
                paymentKey, 400, TossStub.error("ALREADY_CANCELED_PAYMENT", "이미 취소된 결제 입니다."));
        assertThat(adapter.cancel(paymentKey, "주문 취소", "k"))
                .isEqualTo(new Cancelled("ALREADY_CANCELED_PAYMENT", null));

        TossStub.cancelReturns(
                paymentKey,
                200,
                TossStub.cancelledPayment(paymentKey, orderId, 59_700, "PARTIAL_CANCELED", 1_000));
        assertThat(adapter.cancel(paymentKey, "주문 취소", "k"))
                .isInstanceOf(CancelRejected.class)
                .extracting("code")
                .isEqualTo("BALANCE_REMAINING");
    }

    @ParameterizedTest
    @CsvSource({
        "500, FAILED_INTERNAL_SYSTEM_PROCESSING",
        "400, PROVIDER_ERROR",
        "403, FORBIDDEN_CONSECUTIVE_REQUEST",
        "409, IDEMPOTENT_REQUEST_PROCESSING"
    })
    @DisplayName("일시적인 취소 오류는 알 수 없음이다(다시 시도한다)")
    void unknownCancel(int status, String code) {
        TossStub.cancelReturns(paymentKey, status, TossStub.error(code, "메시지"));

        assertThat(adapter.cancel(paymentKey, "주문 취소", "k")).isEqualTo(new Unknown(code));
    }

    @Test
    @DisplayName("그 밖의 취소 오류는 거절이고, 응답이 늦으면 TIMEOUT이다")
    void rejectedAndTimeoutCancel() {
        TossStub.cancelReturns(
                paymentKey, 403, TossStub.error("NOT_CANCELABLE_PAYMENT", "취소 할 수 없는 결제 입니다."));
        assertThat(adapter.cancel(paymentKey, "주문 취소", "k"))
                .isEqualTo(new CancelRejected("NOT_CANCELABLE_PAYMENT", "취소 할 수 없는 결제 입니다."));

        TossStub.cancelDelayed(
                paymentKey,
                1_500,
                TossStub.cancelledPayment(paymentKey, orderId, 59_700, "CANCELED", 0));
        assertThat(adapter.cancel(paymentKey, "주문 취소", "k")).isEqualTo(new Unknown("TIMEOUT"));
    }
}
