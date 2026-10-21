package com.openmarket.payment.command.application;

import com.openmarket.common.messaging.outbox.Outbox;
import com.openmarket.payment.command.application.PaymentGatewayPort.CancelRejected;
import com.openmarket.payment.command.application.PaymentGatewayPort.Cancelled;
import com.openmarket.payment.command.application.PaymentGatewayPort.Unknown;
import com.openmarket.payment.command.domain.Payment;
import com.openmarket.payment.command.domain.PaymentCancelled;
import com.openmarket.payment.command.domain.PaymentLedgerEntry;
import com.openmarket.payment.command.domain.PaymentLedgerRepository;
import com.openmarket.payment.command.domain.PaymentRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * 취소 요청이 있는 승인된 결제를 토스에서 전액 취소한다(PAY-3, docs/architecture/payment.md "보상 취소"). 토스는 트랜잭션 밖에서 부르고,
 * 응답을 원장에 남긴 뒤 결제를 다시 잠그고 반영한다. 취소되면 {@code PaymentCancelled}를 낸다. 결과를 알 수 없으면 그대로 두어 다음 주기에 같은 멱등
 * 키로 다시 부른다. 토스가 거절하면 실패 코드를 남기고 멈춘다.
 */
@Service
public class CancelApprovedPaymentUseCase {

    static final String CANCEL_REASON = "주문 취소";

    private static final Logger log = LoggerFactory.getLogger(CancelApprovedPaymentUseCase.class);

    private final PaymentRepository payments;
    private final PaymentLedgerRepository ledger;
    private final PaymentGatewayPort gateway;
    private final Outbox outbox;
    private final TransactionOperations transaction;
    private final Clock clock;

    public CancelApprovedPaymentUseCase(
            PaymentRepository payments,
            PaymentLedgerRepository ledger,
            PaymentGatewayPort gateway,
            Outbox outbox,
            TransactionOperations transaction,
            Clock clock) {
        this.payments = payments;
        this.ledger = ledger;
        this.gateway = gateway;
        this.outbox = outbox;
        this.transaction = transaction;
        this.clock = clock;
    }

    public void execute(UUID paymentId) {
        Optional<Target> target =
                transaction.execute(
                        _ ->
                                payments.findForUpdateById(paymentId)
                                        .filter(Payment::needsGatewayCancel)
                                        .map(CancelApprovedPaymentUseCase::target));
        if (target == null || target.isEmpty()) {
            return;
        }
        Target found = target.get();
        PaymentGatewayPort.CancelResult result =
                gateway.cancel(found.paymentKey(), CANCEL_REASON, found.idempotencyKey());
        transaction.executeWithoutResult(_ -> apply(paymentId, found.paymentKey(), result));
    }

    private void apply(UUID paymentId, String paymentKey, PaymentGatewayPort.CancelResult result) {
        Instant now = clock.instant();
        ledger.save(entry(paymentId, paymentKey, result, now));
        Payment payment =
                payments.findForUpdateById(paymentId)
                        .orElseThrow(() -> new IllegalStateException("결제가 없다: " + paymentId));
        switch (result) {
            case Cancelled _ -> {
                if (payment.cancelled(now) == Payment.Transition.APPLIED) {
                    payments.save(payment);
                    outbox.append(
                            ApplyGatewayResultUseCase.TOPIC,
                            payment.orderId().toString(),
                            PaymentCancelled.TYPE,
                            new PaymentCancelled(
                                    payment.orderId(),
                                    payment.id(),
                                    payment.amount(),
                                    PaymentCancelled.Reason.ORDER_CANCELLED,
                                    now));
                }
            }
            case CancelRejected rejected -> {
                if (payment.cancelRejected(rejected.code(), now) == Payment.Transition.APPLIED) {
                    payments.save(payment);
                    log.error(
                            "토스가 보상 취소를 거절했다. 운영자가 처리해야 한다: paymentId={}, code={}",
                            payment.id(),
                            rejected.code());
                }
            }
            case Unknown unknown ->
                    log.warn(
                            "토스 취소 결과를 알 수 없어 다음 주기에 다시 취소한다: paymentId={}, reason={}",
                            payment.id(),
                            unknown.reason());
        }
    }

    private static PaymentLedgerEntry entry(
            UUID paymentId,
            String paymentKey,
            PaymentGatewayPort.CancelResult result,
            Instant now) {
        PaymentLedgerEntry.Kind kind = PaymentLedgerEntry.Kind.CANCEL;
        return switch (result) {
            case Cancelled cancelled ->
                    PaymentLedgerEntry.record(
                            paymentId,
                            kind,
                            PaymentLedgerEntry.Outcome.CANCELLED,
                            cancelled.status(),
                            paymentKey,
                            cancelled.transactionKey(),
                            null,
                            now);
            case CancelRejected rejected ->
                    PaymentLedgerEntry.record(
                            paymentId,
                            kind,
                            PaymentLedgerEntry.Outcome.REJECTED,
                            rejected.code(),
                            paymentKey,
                            null,
                            null,
                            now);
            case Unknown unknown ->
                    PaymentLedgerEntry.record(
                            paymentId,
                            kind,
                            PaymentLedgerEntry.Outcome.UNKNOWN,
                            unknown.reason(),
                            paymentKey,
                            null,
                            null,
                            now);
        };
    }

    private static Target target(Payment payment) {
        String paymentKey = payment.paymentKey();
        if (paymentKey == null) {
            throw new IllegalStateException("결제 키가 없는 승인 결제다: " + payment.id());
        }
        return new Target(paymentKey, payment.gatewayCancelIdempotencyKey());
    }

    private record Target(String paymentKey, String idempotencyKey) {}
}
