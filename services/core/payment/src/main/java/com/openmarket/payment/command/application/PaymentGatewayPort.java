package com.openmarket.payment.command.application;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 토스페이먼츠 결제 API(docs/architecture/payment.md "토스 연동"). 구현은 {@code infra}의 {@code
 * TossPaymentGatewayAdapter}다. 토스 응답 형식은 이 포트 밖으로 나오지 않고 네 가지 결과로만 알린다.
 */
public interface PaymentGatewayPort {

    /** 승인을 요청한다. 같은 {@code idempotencyKey}로 다시 보내도 토스가 한 번만 처리한다. */
    Result confirm(String paymentKey, UUID orderId, long amount, String idempotencyKey);

    /** 결제를 조회한다. */
    Result find(String paymentKey);

    /** 승인된 결제를 전액 취소한다. 같은 {@code idempotencyKey}로 다시 보내도 토스가 한 번만 처리한다. */
    CancelResult cancel(String paymentKey, String reason, String idempotencyKey);

    /** 승인된 결제의 일부를 취소한다(환불). 같은 {@code idempotencyKey}로 다시 보내도 토스가 한 번만 처리한다. */
    RefundResult refund(String paymentKey, long amount, String reason, String idempotencyKey);

    /** 토스 응답의 해석. */
    sealed interface Result permits Approved, Rejected, Pending, Unknown {}

    /** 토스 취소 응답의 해석. */
    sealed interface CancelResult permits Cancelled, CancelRejected, Unknown {}

    /** 토스 부분취소(환불) 응답의 해석. */
    sealed interface RefundResult permits Refunded, CancelRejected, Unknown {}

    /**
     * 승인됐다.
     *
     * @param status 토스 결제 상태(원장에 남긴다)
     * @param totalAmount 승인 금액. 결제수단 즉시 할인이 있어도 요청 금액이다
     * @param method 토스 결제수단 이름
     * @param transactionKey 토스 응답의 마지막 거래 키
     */
    record Approved(
            String status,
            long totalAmount,
            String method,
            Instant approvedAt,
            @Nullable String transactionKey)
            implements Result {}

    /**
     * 거절됐거나 결제가 끝내 이뤄지지 않았다. 돈이 빠져나가지 않았다.
     *
     * @param code 토스 오류 코드, 또는 조회한 토스 결제 상태
     * @param message 구매자에게 보여 줄 수 있는 토스 메시지
     */
    record Rejected(String code, String message) implements Result {}

    /**
     * 토스에서 아직 정해지지 않았다(조회).
     *
     * @param status 토스 결제 상태
     */
    record Pending(String status) implements Result {}

    /**
     * 응답으로 정할 수 없다. 토스가 처리했을 수도 있다.
     *
     * @param reason 토스 오류 코드, 또는 {@code TIMEOUT}, {@code CONNECTION_FAILED}, {@code HTTP_<상태>}
     */
    record Unknown(String reason) implements Result, CancelResult, RefundResult {}

    /**
     * 전액 취소됐다(남은 금액이 0이다). 이미 취소된 결제였어도 같다.
     *
     * @param status 토스 결제 상태, 또는 이미 취소됐을 때의 토스 오류 코드(원장에 남긴다)
     * @param transactionKey 취소 거래 키. 이미 취소된 결제면 없다
     */
    record Cancelled(String status, @Nullable String transactionKey) implements CancelResult {}

    /**
     * 토스가 취소(전액, 부분)를 거절했다. 다시 보내도 결과가 같다.
     *
     * @param code 토스 오류 코드
     */
    record CancelRejected(String code, String message) implements CancelResult, RefundResult {}

    /**
     * 부분취소됐다.
     *
     * @param status 토스 결제 상태(원장에 남긴다)
     * @param transactionKey 이 취소의 거래 키
     */
    record Refunded(String status, @Nullable String transactionKey) implements RefundResult {}
}
