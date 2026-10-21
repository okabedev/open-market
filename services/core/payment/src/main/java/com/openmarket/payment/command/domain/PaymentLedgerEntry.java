package com.openmarket.payment.command.domain;

import com.openmarket.common.core.id.Ids;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 결제 원장(PAY-4, docs/glossary.md "결제 원장"). 토스 요청 하나마다 한 줄이고 고치지 않는다. 응답 원문은 남기지 않는다(결제수단 상세가 들어 있다).
 * 필드는 docs/architecture/payment.md "원장".
 */
@Entity
@Table(name = "payment_ledger_entry")
public class PaymentLedgerEntry {

    @Id private UUID id;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Kind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Outcome outcome;

    @Column(name = "pg_code")
    private @Nullable String pgCode;

    @Column(name = "payment_key", nullable = false)
    private String paymentKey;

    @Column(name = "transaction_key")
    private @Nullable String transactionKey;

    @Column private @Nullable Long amount;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected PaymentLedgerEntry() {}

    private PaymentLedgerEntry(
            UUID paymentId,
            Kind kind,
            Outcome outcome,
            @Nullable String pgCode,
            String paymentKey,
            @Nullable String transactionKey,
            @Nullable Long amount,
            Instant occurredAt) {
        this.id = Ids.newId();
        this.paymentId = paymentId;
        this.kind = kind;
        this.outcome = outcome;
        this.pgCode = pgCode;
        this.paymentKey = paymentKey;
        this.transactionKey = transactionKey;
        this.amount = amount;
        this.occurredAt = occurredAt;
    }

    public static PaymentLedgerEntry record(
            UUID paymentId,
            Kind kind,
            Outcome outcome,
            @Nullable String pgCode,
            String paymentKey,
            @Nullable String transactionKey,
            @Nullable Long amount,
            Instant occurredAt) {
        return new PaymentLedgerEntry(
                paymentId, kind, outcome, pgCode, paymentKey, transactionKey, amount, occurredAt);
    }

    public UUID paymentId() {
        return paymentId;
    }

    public Kind kind() {
        return kind;
    }

    public @Nullable Long amount() {
        return amount;
    }

    public Outcome outcome() {
        return outcome;
    }

    public @Nullable String pgCode() {
        return pgCode;
    }

    /** 토스 요청 종류. */
    public enum Kind {
        /** 승인 */
        CONFIRM,
        /** 결제 조회 */
        QUERY,
        /** 전액 취소 */
        CANCEL,
        /** 부분취소(환불) */
        REFUND
    }

    /** 토스 응답의 해석. */
    public enum Outcome {
        /** 승인됐다. */
        APPROVED,
        /** 거절됐거나 결제가 실패했다. */
        REJECTED,
        /** 아직 정해지지 않았다(조회). */
        PENDING,
        /** 응답으로 정할 수 없다(타임아웃, 5xx 등). */
        UNKNOWN,
        /** 취소됐다. */
        CANCELLED
    }
}
