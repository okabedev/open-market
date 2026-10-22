package com.openmarket.payment.command.domain;

import com.openmarket.common.core.id.Ids;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 환불(docs/glossary.md "환불"). 클레임 하나에 하나이고(유일 인덱스), 하위 주문 금액에서 정한 금액을 토스 부분취소로 돌려준다. 상태 규칙은
 * docs/architecture/payment.md "환불".
 */
@Entity
@Table(name = "refund")
public class Refund {

    @Id private UUID id;

    @Column(name = "claim_id", nullable = false, unique = true)
    private UUID claimId;

    // 결제가 없어 환불할 수 없었으면 비어 있다.
    @Column(name = "payment_id")
    private @Nullable UUID paymentId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "seller_order_id", nullable = false)
    private UUID sellerOrderId;

    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RefundStatus status;

    // 토스 부분취소 시도 번호. 토스 Idempotency-Key에 들어간다. 관리자 재시도가 올린다.
    @Column(nullable = false)
    private int attempt;

    @Column(name = "failure_code")
    private @Nullable String failureCode;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "completed_at")
    private @Nullable Instant completedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version private long version;

    protected Refund() {}

    private Refund(
            @Nullable UUID paymentId,
            UUID orderId,
            UUID claimId,
            UUID sellerOrderId,
            long amount,
            RefundStatus status,
            Instant now) {
        if (amount < 0) {
            throw new IllegalArgumentException("환불 금액은 0 이상이다: " + amount);
        }
        this.id = Ids.newId();
        this.claimId = claimId;
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.sellerOrderId = sellerOrderId;
        this.amount = amount;
        this.status = status;
        this.attempt = 1;
        this.requestedAt = now;
        this.updatedAt = now;
        if (status == RefundStatus.COMPLETED) {
            this.completedAt = now;
        }
    }

    /** 결제에 환불을 요청한다. 금액이 0이면 토스를 부르지 않고 바로 {@code COMPLETED}다. */
    public static Refund requested(
            Payment payment, UUID claimId, UUID sellerOrderId, long amount, Instant now) {
        return new Refund(
                payment.id(),
                payment.orderId(),
                claimId,
                sellerOrderId,
                amount,
                amount == 0 ? RefundStatus.COMPLETED : RefundStatus.REQUESTED,
                now);
    }

    /** 환불할 수 없어 토스를 부르지 않고 {@code FAILED}로 남긴다(결제가 없거나 남은 금액이 모자란다). */
    public static Refund rejected(
            UUID orderId,
            UUID claimId,
            UUID sellerOrderId,
            long amount,
            String failureCode,
            Instant now) {
        Refund refund =
                new Refund(null, orderId, claimId, sellerOrderId, amount, RefundStatus.FAILED, now);
        refund.failureCode = failureCode;
        return refund;
    }

    /** 토스가 부분취소했다. */
    public Transition complete(Instant now) {
        if (status != RefundStatus.REQUESTED) {
            return Transition.ALREADY;
        }
        status = RefundStatus.COMPLETED;
        completedAt = now;
        updatedAt = now;
        return Transition.APPLIED;
    }

    /** 토스가 부분취소를 거절했다. */
    public Transition fail(String code, Instant now) {
        if (status != RefundStatus.REQUESTED) {
            return Transition.ALREADY;
        }
        status = RefundStatus.FAILED;
        failureCode = code;
        updatedAt = now;
        return Transition.APPLIED;
    }

    /**
     * 관리자 환불 재시도(docs/architecture/payment.md "관리자 재시도"). 환불 요청과 같은 확인을 다시 하고, 통과하면 {@code
     * REQUESTED}로 돌리고 시도 번호를 올린다. 확인을 통과하지 못하면 {@code FAILED}로 둔다.
     *
     * @param payment 주문의 결제. 결제가 없으면 비어 있다
     * @param available 결제의 남은 금액에서 진행 중인 환불 금액을 뺀 금액(원)
     * @return 재시도를 시작하면 {@code APPLIED}, 이미 {@code REQUESTED}면 {@code ALREADY}
     * @throws PaymentNotRetryableException 끝난 환불이다
     * @throws PaymentNotRefundableException 결제가 없거나 환불할 수 없는 상태다
     * @throws RefundAmountExceededException 남은 금액보다 크다
     */
    public Transition retry(@Nullable Payment payment, long available, Instant now) {
        if (status == RefundStatus.REQUESTED) {
            return Transition.ALREADY;
        }
        if (status != RefundStatus.FAILED) {
            throw new PaymentNotRetryableException();
        }
        if (payment == null || !payment.isRefundable()) {
            throw new PaymentNotRefundableException();
        }
        if (amount > available) {
            throw new RefundAmountExceededException();
        }
        paymentId = payment.id();
        status = RefundStatus.REQUESTED;
        failureCode = null;
        attempt++;
        updatedAt = now;
        return Transition.APPLIED;
    }

    /** 토스 부분취소의 {@code Idempotency-Key}. 같은 시도를 다시 보내도 토스가 한 번만 처리한다. */
    public String gatewayIdempotencyKey() {
        return id + "-" + attempt;
    }

    public UUID id() {
        return id;
    }

    public UUID claimId() {
        return claimId;
    }

    public @Nullable UUID paymentId() {
        return paymentId;
    }

    public UUID orderId() {
        return orderId;
    }

    public UUID sellerOrderId() {
        return sellerOrderId;
    }

    public long amount() {
        return amount;
    }

    public RefundStatus status() {
        return status;
    }

    public @Nullable String failureCode() {
        return failureCode;
    }

    public @Nullable Instant completedAt() {
        return completedAt;
    }

    /** 상태 전이 결과. */
    public enum Transition {
        /** 바꿨다. */
        APPLIED,
        /** 이미 끝난 환불이다. 아무것도 하지 않았다. */
        ALREADY
    }
}
