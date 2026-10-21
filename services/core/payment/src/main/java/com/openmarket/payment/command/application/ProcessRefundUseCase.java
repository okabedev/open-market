package com.openmarket.payment.command.application;

import com.openmarket.common.messaging.outbox.Outbox;
import com.openmarket.payment.command.application.PaymentGatewayPort.CancelRejected;
import com.openmarket.payment.command.application.PaymentGatewayPort.Refunded;
import com.openmarket.payment.command.application.PaymentGatewayPort.Unknown;
import com.openmarket.payment.command.domain.Payment;
import com.openmarket.payment.command.domain.PaymentLedgerEntry;
import com.openmarket.payment.command.domain.PaymentLedgerRepository;
import com.openmarket.payment.command.domain.PaymentRepository;
import com.openmarket.payment.command.domain.Refund;
import com.openmarket.payment.command.domain.RefundCompleted;
import com.openmarket.payment.command.domain.RefundFailed;
import com.openmarket.payment.command.domain.RefundRepository;
import com.openmarket.payment.command.domain.RefundStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * {@code REQUESTED} 환불의 토스 부분취소를 부른다(docs/architecture/payment.md "환불 작업"). 토스는 트랜잭션 밖에서 부르고, 응답을
 * 원장에 남긴 뒤 결제와 환불을 다시 잠그고 반영한다. 결과를 알 수 없으면 그대로 두어 다음 주기에 같은 멱등 키로 다시 부른다.
 */
@Service
public class ProcessRefundUseCase {

    static final String REFUND_REASON = "클레임 환불";

    private static final Logger log = LoggerFactory.getLogger(ProcessRefundUseCase.class);

    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final PaymentLedgerRepository ledger;
    private final PaymentGatewayPort gateway;
    private final Outbox outbox;
    private final TransactionOperations transaction;
    private final Clock clock;

    public ProcessRefundUseCase(
            PaymentRepository payments,
            RefundRepository refunds,
            PaymentLedgerRepository ledger,
            PaymentGatewayPort gateway,
            Outbox outbox,
            TransactionOperations transaction,
            Clock clock) {
        this.payments = payments;
        this.refunds = refunds;
        this.ledger = ledger;
        this.gateway = gateway;
        this.outbox = outbox;
        this.transaction = transaction;
        this.clock = clock;
    }

    public void execute(UUID refundId) {
        Optional<Target> target = transaction.execute(_ -> target(refundId));
        if (target == null || target.isEmpty()) {
            return;
        }
        Target found = target.get();
        PaymentGatewayPort.RefundResult result =
                gateway.refund(
                        found.paymentKey(), found.amount(), REFUND_REASON, found.idempotencyKey());
        transaction.executeWithoutResult(_ -> apply(refundId, found, result));
    }

    private Optional<Target> target(UUID refundId) {
        Optional<Refund> refund =
                refunds.findForUpdateById(refundId)
                        .filter(found -> found.status() == RefundStatus.REQUESTED);
        if (refund.isEmpty()) {
            return Optional.empty();
        }
        UUID paymentId = Objects.requireNonNull(refund.get().paymentId());
        Payment payment =
                payments.findForUpdateById(paymentId)
                        .orElseThrow(() -> new IllegalStateException("결제가 없다: " + paymentId));
        String paymentKey = payment.paymentKey();
        if (paymentKey == null) {
            throw new IllegalStateException("결제 키가 없는 승인 결제다: " + paymentId);
        }
        return Optional.of(
                new Target(
                        paymentId,
                        paymentKey,
                        refund.get().amount(),
                        refund.get().gatewayIdempotencyKey()));
    }

    private void apply(UUID refundId, Target target, PaymentGatewayPort.RefundResult result) {
        Instant now = clock.instant();
        ledger.save(entry(target, result, now));
        // 결제를 먼저 잠근다. 같은 결제의 환불과 환불 요청이 남은 금액을 같은 순서로 본다.
        Payment payment =
                payments.findForUpdateById(target.paymentId())
                        .orElseThrow(
                                () -> new IllegalStateException("결제가 없다: " + target.paymentId()));
        Refund refund =
                refunds.findForUpdateById(refundId)
                        .orElseThrow(() -> new IllegalStateException("환불이 없다: " + refundId));
        switch (result) {
            case Refunded _ -> {
                if (refund.complete(now) == Refund.Transition.APPLIED) {
                    payment.refunded(refund.amount(), now);
                    payments.save(payment);
                    refunds.save(refund);
                    outbox.append(
                            ApplyGatewayResultUseCase.TOPIC,
                            refund.orderId().toString(),
                            RefundCompleted.TYPE,
                            new RefundCompleted(
                                    refund.orderId(),
                                    refund.id(),
                                    refund.claimId(),
                                    refund.sellerOrderId(),
                                    refund.amount(),
                                    now));
                }
            }
            case CancelRejected rejected -> {
                if (refund.fail(rejected.code(), now) == Refund.Transition.APPLIED) {
                    refunds.save(refund);
                    log.error(
                            "토스가 환불을 거절했다. 관리자가 처리해야 한다: refundId={}, code={}",
                            refund.id(),
                            rejected.code());
                    outbox.append(
                            ApplyGatewayResultUseCase.TOPIC,
                            refund.orderId().toString(),
                            RefundFailed.TYPE,
                            RefundFailed.of(refund));
                }
            }
            case Unknown unknown ->
                    log.warn(
                            "토스 환불 결과를 알 수 없어 다음 주기에 다시 부른다: refundId={}, reason={}",
                            refund.id(),
                            unknown.reason());
        }
    }

    private static PaymentLedgerEntry entry(
            Target target, PaymentGatewayPort.RefundResult result, Instant now) {
        return switch (result) {
            case Refunded refunded ->
                    record(
                            target,
                            PaymentLedgerEntry.Outcome.CANCELLED,
                            refunded.status(),
                            refunded.transactionKey(),
                            target.amount(),
                            now);
            case CancelRejected rejected ->
                    record(
                            target,
                            PaymentLedgerEntry.Outcome.REJECTED,
                            rejected.code(),
                            null,
                            null,
                            now);
            case Unknown unknown ->
                    record(
                            target,
                            PaymentLedgerEntry.Outcome.UNKNOWN,
                            unknown.reason(),
                            null,
                            null,
                            now);
        };
    }

    private static PaymentLedgerEntry record(
            Target target,
            PaymentLedgerEntry.Outcome outcome,
            String pgCode,
            @Nullable String transactionKey,
            @Nullable Long amount,
            Instant now) {
        return PaymentLedgerEntry.record(
                target.paymentId(),
                PaymentLedgerEntry.Kind.REFUND,
                outcome,
                pgCode,
                target.paymentKey(),
                transactionKey,
                amount,
                now);
    }

    private record Target(UUID paymentId, String paymentKey, long amount, String idempotencyKey) {}
}
