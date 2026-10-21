package com.openmarket.payment.command.application;

import com.openmarket.common.messaging.outbox.Outbox;
import com.openmarket.payment.command.application.PaymentGatewayPort.Approved;
import com.openmarket.payment.command.application.PaymentGatewayPort.Pending;
import com.openmarket.payment.command.application.PaymentGatewayPort.Rejected;
import com.openmarket.payment.command.application.PaymentGatewayPort.Unknown;
import com.openmarket.payment.command.domain.Payment;
import com.openmarket.payment.command.domain.PaymentApproved;
import com.openmarket.payment.command.domain.PaymentFailed;
import com.openmarket.payment.command.domain.PaymentLedgerEntry;
import com.openmarket.payment.command.domain.PaymentLedgerRepository;
import com.openmarket.payment.command.domain.PaymentRepository;
import com.openmarket.payment.command.domain.PaymentStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 토스 응답 하나를 원장에 남기고 결제에 반영한다(docs/architecture/payment.md "승인", "원장"). 결제 행을 잠그고, 응답이 지금 승인 시도의 것일
 * 때만 상태를 바꾼다. 승인되면 {@code PaymentApproved}, 실패면 {@code PaymentFailed}를 낸다. 승인과 재조회가 함께 쓴다.
 */
@Service
public class ApplyGatewayResultUseCase {

    static final String TOPIC = "payment.payment";

    private static final Logger log = LoggerFactory.getLogger(ApplyGatewayResultUseCase.class);

    private final PaymentRepository payments;
    private final PaymentLedgerRepository ledger;
    private final Outbox outbox;
    private final Clock clock;

    public ApplyGatewayResultUseCase(
            PaymentRepository payments,
            PaymentLedgerRepository ledger,
            Outbox outbox,
            Clock clock) {
        this.payments = payments;
        this.ledger = ledger;
        this.outbox = outbox;
        this.clock = clock;
    }

    /**
     * 반영한 뒤의 결제 상태.
     *
     * @param attempt 응답을 받은 승인 시도 번호
     */
    @Transactional
    public Applied execute(
            UUID paymentId,
            int attempt,
            PaymentLedgerEntry.Kind kind,
            String paymentKey,
            PaymentGatewayPort.Result result) {
        Instant now = clock.instant();
        ledger.save(entry(paymentId, kind, paymentKey, result, now));
        Payment payment =
                payments.findForUpdateById(paymentId)
                        .orElseThrow(() -> new IllegalStateException("결제가 없다: " + paymentId));
        Payment.Transition transition =
                switch (result) {
                    case Approved approved -> approve(payment, attempt, approved, now);
                    case Rejected rejected -> fail(payment, attempt, rejected.code(), now);
                    case Pending _, Unknown _ -> Payment.Transition.ALREADY;
                };
        if (transition == Payment.Transition.STALE) {
            log.warn(
                    "지난 승인 시도이거나 상태가 맞지 않는 토스 응답이라 반영하지 않는다: paymentId={}, attempt={}, status={}",
                    paymentId,
                    attempt,
                    payment.status());
        }
        return new Applied(payment.status(), payment.method(), payment.failureCode());
    }

    private Payment.Transition approve(
            Payment payment, int attempt, Approved approved, Instant now) {
        Payment.Transition transition =
                payment.approve(attempt, approved.method(), approved.approvedAt(), now);
        if (transition == Payment.Transition.APPLIED) {
            if (approved.totalAmount() != payment.amount()) {
                log.error(
                        "토스 승인 금액이 결제 금액과 다르다: paymentId={}, amount={}, totalAmount={}",
                        payment.id(),
                        payment.amount(),
                        approved.totalAmount());
            }
            payments.save(payment);
            outbox.append(
                    TOPIC,
                    payment.orderId().toString(),
                    PaymentApproved.TYPE,
                    new PaymentApproved(
                            payment.orderId(),
                            payment.id(),
                            approved.totalAmount(),
                            approved.method(),
                            approved.approvedAt()));
        }
        return transition;
    }

    private Payment.Transition fail(Payment payment, int attempt, String code, Instant now) {
        Payment.Transition transition = payment.fail(attempt, code, now);
        if (transition == Payment.Transition.APPLIED) {
            payments.save(payment);
            outbox.append(
                    TOPIC,
                    payment.orderId().toString(),
                    PaymentFailed.TYPE,
                    new PaymentFailed(payment.orderId(), payment.id(), code));
        }
        return transition;
    }

    private static PaymentLedgerEntry entry(
            UUID paymentId,
            PaymentLedgerEntry.Kind kind,
            String paymentKey,
            PaymentGatewayPort.Result result,
            Instant now) {
        return switch (result) {
            case Approved approved ->
                    PaymentLedgerEntry.record(
                            paymentId,
                            kind,
                            PaymentLedgerEntry.Outcome.APPROVED,
                            approved.status(),
                            paymentKey,
                            approved.transactionKey(),
                            approved.totalAmount(),
                            now);
            case Rejected rejected ->
                    record(
                            paymentId,
                            kind,
                            PaymentLedgerEntry.Outcome.REJECTED,
                            rejected.code(),
                            paymentKey,
                            now);
            case Pending pending ->
                    record(
                            paymentId,
                            kind,
                            PaymentLedgerEntry.Outcome.PENDING,
                            pending.status(),
                            paymentKey,
                            now);
            case Unknown unknown ->
                    record(
                            paymentId,
                            kind,
                            PaymentLedgerEntry.Outcome.UNKNOWN,
                            unknown.reason(),
                            paymentKey,
                            now);
        };
    }

    private static PaymentLedgerEntry record(
            UUID paymentId,
            PaymentLedgerEntry.Kind kind,
            PaymentLedgerEntry.Outcome outcome,
            String pgCode,
            String paymentKey,
            Instant now) {
        return PaymentLedgerEntry.record(
                paymentId, kind, outcome, pgCode, paymentKey, null, null, now);
    }

    /**
     * 반영한 뒤의 결제.
     *
     * @param method 승인됐을 때의 토스 결제수단 이름
     * @param failureCode 실패했을 때의 코드
     */
    public record Applied(
            PaymentStatus status, @Nullable String method, @Nullable String failureCode) {}
}
