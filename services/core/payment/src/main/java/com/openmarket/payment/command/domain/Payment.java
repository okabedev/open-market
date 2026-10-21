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
     * @throws PaymentAmountMismatchException 금액이 결제 금액과 다르다
     * @throws PaymentNotConfirmableException 다른 결제 키로 이미 요청했거나 승인됐다
     */
    public ConfirmRequest requestConfirm(String paymentKey, long amount, Instant now) {
        return switch (status) {
            case IN_PROGRESS -> sameKey(paymentKey, ConfirmRequest.PENDING);
            case APPROVED -> sameKey(paymentKey, ConfirmRequest.APPROVED);
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

    /** 승인 실패를 반영한다. {@code attempt}가 지금 시도일 때만 바꾼다. */
    public Transition fail(int attempt, String code, Instant now) {
        if (attempt != confirmAttempt) {
            return Transition.STALE;
        }
        if (status == PaymentStatus.FAILED) {
            return Transition.ALREADY;
        }
        if (status != PaymentStatus.IN_PROGRESS) {
            return Transition.STALE;
        }
        status = PaymentStatus.FAILED;
        failureCode = code;
        updatedAt = now;
        return Transition.APPLIED;
    }

    /** 지금 승인 시도의 토스 {@code Idempotency-Key}. 같은 시도를 다시 보내도 토스가 한 번만 처리한다. */
    public String gatewayIdempotencyKey() {
        return id + "-" + confirmAttempt;
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

    /** 승인 요청의 결과. */
    public enum ConfirmRequest {
        /** {@code IN_PROGRESS}로 바꿨다. 토스를 부른다. */
        STARTED,
        /** 같은 결제 키로 이미 요청했고 결과를 기다린다. */
        PENDING,
        /** 같은 결제 키로 이미 승인됐다. */
        APPROVED
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
