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
 * 결제(docs/glossary.md "결제"). 주문 하나당 하나이고 주문의 금액을 고정한다. 상태 규칙은 docs/architecture/payment.md "결제",
 * "승인".
 */
@Entity
@Table(name = "payment")
public class Payment {

    @Id private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "buyer_id", nullable = false)
    private UUID buyerId;

    @Column(nullable = false)
    private long amount;

    @Column(name = "order_name", nullable = false)
    private String orderName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(name = "payment_key")
    private @Nullable String paymentKey;

    @Column(name = "confirm_attempt", nullable = false)
    private int confirmAttempt;

    @Column(name = "requested_at")
    private @Nullable Instant requestedAt;

    @Column private @Nullable String method;

    @Column(name = "approved_at")
    private @Nullable Instant approvedAt;

    @Column(name = "failure_code")
    private @Nullable String failureCode;

    @Column(name = "cancel_requested_at")
    private @Nullable Instant cancelRequestedAt;

    @Column(name = "cancelled_at")
    private @Nullable Instant cancelledAt;

    @Column(name = "cancel_failure_code")
    private @Nullable String cancelFailureCode;

    // 환불(부분취소)로 돌려준 금액의 합(원).
    @Column(name = "refunded_amount", nullable = false)
    private long refundedAmount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version private long version;

    protected Payment() {}

    private Payment(UUID orderId, UUID buyerId, long amount, String orderName, Instant now) {
        this.id = Ids.newId();
        this.orderId = orderId;
        this.buyerId = buyerId;
        this.amount = amount;
        this.orderName = orderName;
        this.status = PaymentStatus.READY;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** 결제를 기다리는 주문({@code OrderAwaitingPayment})의 결제. */
    public static Payment ready(
            UUID orderId, UUID buyerId, long amount, String orderName, Instant now) {
        return new Payment(orderId, buyerId, amount, orderName, now);
    }

    public boolean isOwnedBy(UUID buyerId) {
        return this.buyerId.equals(buyerId);
    }

    /**
     * 승인을 요청한다. {@code READY}, {@code FAILED}면 금액을 대조하고 {@code IN_PROGRESS}로 바꾼 뒤 시도 번호를 올린다. 이미
     * 요청했거나 승인된 결제는 같은 결제 키면 지금 상태를 알리고 바꾸지 않는다.
     *
     * @throws PaymentCancelledException 주문이 취소돼 결제가 취소됐거나 취소를 기다린다
     * @throws PaymentAmountMismatchException 금액이 결제 금액과 다르다
     * @throws PaymentNotConfirmableException 다른 결제 키로 이미 요청했거나 승인됐다
     */
    public ConfirmRequest requestConfirm(String paymentKey, long amount, Instant now) {
        if (isCancelRequested()) {
            throw new PaymentCancelledException();
        }
        return switch (status) {
            case CANCELLED -> throw new PaymentCancelledException();
            case IN_PROGRESS -> sameKey(paymentKey, ConfirmRequest.PENDING);
            case APPROVED, PARTIALLY_CANCELLED -> sameKey(paymentKey, ConfirmRequest.APPROVED);
            case READY, FAILED -> {
                if (amount != this.amount) {
                    throw new PaymentAmountMismatchException();
                }
                status = PaymentStatus.IN_PROGRESS;
                this.paymentKey = paymentKey;
                confirmAttempt++;
                requestedAt = now;
                failureCode = null;
                updatedAt = now;
                yield ConfirmRequest.STARTED;
            }
        };
    }

    private ConfirmRequest sameKey(String paymentKey, ConfirmRequest request) {
        if (!paymentKey.equals(this.paymentKey)) {
            throw new PaymentNotConfirmableException();
        }
        return request;
    }

    /** 승인을 반영한다. {@code attempt}가 지금 시도일 때만 바꾼다. */
    public Transition approve(int attempt, String method, Instant approvedAt, Instant now) {
        if (attempt != confirmAttempt) {
            return Transition.STALE;
        }
        if (status == PaymentStatus.APPROVED) {
            return Transition.ALREADY;
        }
        if (status != PaymentStatus.IN_PROGRESS) {
            return Transition.STALE;
        }
        status = PaymentStatus.APPROVED;
        this.method = method;
        this.approvedAt = approvedAt;
        updatedAt = now;
        return Transition.APPLIED;
    }

    /**
     * 승인 실패를 반영한다. {@code attempt}가 지금 시도일 때만 바꾼다. 취소 요청이 있으면 다시 결제할 주문이 없으므로 {@code CANCELLED}가
     * 된다.
     */
    public Transition fail(int attempt, String code, Instant now) {
        if (attempt != confirmAttempt) {
            return Transition.STALE;
        }
        if (status == PaymentStatus.FAILED
                || (status == PaymentStatus.CANCELLED && code.equals(failureCode))) {
            return Transition.ALREADY;
        }
        if (status != PaymentStatus.IN_PROGRESS) {
            return Transition.STALE;
        }
        failureCode = code;
        if (isCancelRequested()) {
            status = PaymentStatus.CANCELLED;
            cancelledAt = now;
        } else {
            status = PaymentStatus.FAILED;
        }
        updatedAt = now;
        return Transition.APPLIED;
    }

    /**
     * 주문이 결제 전에 취소됐다(docs/architecture/payment.md "보상 취소"). 돈이 빠져나가지 않은 결제({@code READY}, {@code
     * FAILED})는 바로 {@code CANCELLED}다. 승인을 기다리거나 승인된 결제는 취소 요청만 남긴다. 승인되면 토스 전액 취소 대상이 된다.
     */
    public CancelRequest requestCancel(Instant now) {
        if (status == PaymentStatus.CANCELLED || isCancelRequested()) {
            return CancelRequest.ALREADY;
        }
        cancelRequestedAt = now;
        updatedAt = now;
        if (status == PaymentStatus.READY || status == PaymentStatus.FAILED) {
            status = PaymentStatus.CANCELLED;
            cancelledAt = now;
            return CancelRequest.CANCELLED;
        }
        return CancelRequest.REQUESTED;
    }

    /** 토스 전액 취소를 불러야 하는지. 승인됐고 취소 요청이 있으며 토스가 취소를 거절한 적이 없다. */
    public boolean needsGatewayCancel() {
        return status == PaymentStatus.APPROVED && isCancelRequested() && cancelFailureCode == null;
    }

    /** 토스 전액 취소가 끝났다. */
    public Transition cancelled(Instant now) {
        if (status == PaymentStatus.CANCELLED) {
            return Transition.ALREADY;
        }
        if (!needsGatewayCancel()) {
            return Transition.STALE;
        }
        status = PaymentStatus.CANCELLED;
        cancelledAt = now;
        updatedAt = now;
        return Transition.APPLIED;
    }

    /** 토스가 전액 취소를 거절했다. {@code APPROVED}로 두고 더는 자동으로 취소하지 않는다. 운영자가 처리한다. */
    public Transition cancelRejected(String code, Instant now) {
        if (!needsGatewayCancel()) {
            return Transition.STALE;
        }
        cancelFailureCode = code;
        updatedAt = now;
        return Transition.APPLIED;
    }

    /** 환불(부분취소)할 수 있는가. 승인됐거나 일부를 환불했고, 결제 전 취소(보상 취소) 대상이 아니다. */
    public boolean isRefundable() {
        return (status == PaymentStatus.APPROVED || status == PaymentStatus.PARTIALLY_CANCELLED)
                && !isCancelRequested();
    }

    /** 결제 금액에서 환불한 금액을 뺀 남은 금액(원). */
    public long remainingAmount() {
        return amount - refundedAmount;
    }

    /**
     * 토스 부분취소가 끝났다(docs/architecture/payment.md "환불 작업"). 남은 금액이 0이면 {@code CANCELLED}, 아니면 {@code
     * PARTIALLY_CANCELLED}다.
     *
     * @throws IllegalStateException 환불할 수 없는 결제이거나 남은 금액보다 크다. 환불 요청에서 막으므로 생기면 버그다
     */
    public void refunded(long refundAmount, Instant now) {
        if (!isRefundable() || refundAmount > remainingAmount()) {
            throw new IllegalStateException("환불을 반영할 수 없다: paymentId=" + id + ", status=" + status);
        }
        refundedAmount += refundAmount;
        if (remainingAmount() == 0) {
            status = PaymentStatus.CANCELLED;
            cancelledAt = now;
        } else {
            status = PaymentStatus.PARTIALLY_CANCELLED;
        }
        updatedAt = now;
    }

    private boolean isCancelRequested() {
        return cancelRequestedAt != null;
    }

    /** 지금 승인 시도의 토스 {@code Idempotency-Key}. 같은 시도를 다시 보내도 토스가 한 번만 처리한다. */
    public String gatewayIdempotencyKey() {
        return id + "-" + confirmAttempt;
    }

    /** 토스 전액 취소의 {@code Idempotency-Key}. 전액 취소는 결제마다 한 번이라 결제 ID로 만든다. */
    public String gatewayCancelIdempotencyKey() {
        return id + "-cancel";
    }

    public UUID id() {
        return id;
    }

    public UUID orderId() {
        return orderId;
    }

    public UUID buyerId() {
        return buyerId;
    }

    public long amount() {
        return amount;
    }

    public String orderName() {
        return orderName;
    }

    public PaymentStatus status() {
        return status;
    }

    public @Nullable String paymentKey() {
        return paymentKey;
    }

    public int confirmAttempt() {
        return confirmAttempt;
    }

    public @Nullable Instant requestedAt() {
        return requestedAt;
    }

    public @Nullable String method() {
        return method;
    }

    public @Nullable Instant approvedAt() {
        return approvedAt;
    }

    public @Nullable String failureCode() {
        return failureCode;
    }

    public @Nullable Instant cancelRequestedAt() {
        return cancelRequestedAt;
    }

    public @Nullable Instant cancelledAt() {
        return cancelledAt;
    }

    public @Nullable String cancelFailureCode() {
        return cancelFailureCode;
    }

    /** 승인 요청의 결과. */
    public enum ConfirmRequest {
        /** {@code IN_PROGRESS}로 바꿨다. 토스를 부른다. */
        STARTED,
        /** 같은 결제 키로 이미 요청했고 결과를 기다린다. */
        PENDING,
        /** 같은 결제 키로 이미 승인됐다. */
        APPROVED
    }

    /** 취소 요청의 결과. */
    public enum CancelRequest {
        /** 돈이 빠져나가지 않은 결제라 바로 취소했다. */
        CANCELLED,
        /** 취소 요청을 남겼다. 승인되면 토스 전액 취소를 부른다. */
        REQUESTED,
        /** 이미 취소됐거나 취소 요청이 있다. */
        ALREADY
    }

    /** 결과 반영. */
    public enum Transition {
        /** 바꿨다. */
        APPLIED,
        /** 이미 그 결과다. */
        ALREADY,
        /** 지난 시도의 결과이거나 지금 상태와 맞지 않는다. 버린다. */
        STALE
    }
}
