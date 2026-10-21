package com.openmarket.payment.command.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.openmarket.payment.command.application.PaymentGatewayPort.CancelRejected;
import com.openmarket.payment.command.application.PaymentGatewayPort.Refunded;
import com.openmarket.payment.command.application.PaymentGatewayPort.Unknown;
import com.openmarket.payment.command.domain.Payment;
import com.openmarket.payment.command.domain.PaymentLedgerEntry;
import com.openmarket.payment.command.domain.PaymentStatus;
import com.openmarket.payment.command.domain.Refund;
import com.openmarket.payment.command.domain.RefundCompleted;
import com.openmarket.payment.command.domain.RefundFailed;
import com.openmarket.payment.command.domain.RefundStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

/** 클레임 환불(PAY-2, docs/architecture/payment.md "환불"). */
class RefundUseCasesTest {

    static final Instant NOW = Instant.parse("2026-10-21T00:00:00Z");

    final PaymentFakes.Payments payments = new PaymentFakes.Payments();
    final PaymentFakes.Refunds refunds = new PaymentFakes.Refunds();
    final PaymentFakes.Ledger ledger = new PaymentFakes.Ledger();
    final PaymentFakes.Gateway gateway = new PaymentFakes.Gateway();
    final PaymentFakes.RecordingOutbox outbox = new PaymentFakes.RecordingOutbox();
    final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    final RequestRefundUseCase request = new RequestRefundUseCase(payments, refunds, outbox, clock);
    final ProcessRefundUseCase process =
            new ProcessRefundUseCase(
                    payments,
                    refunds,
                    ledger,
                    gateway,
                    outbox,
                    TransactionOperations.withoutTransaction(),
                    clock);

    final Payment payment = payments.save(approved());

    Payment approved() {
        Payment approved =
                Payment.ready(UUID.randomUUID(), UUID.randomUUID(), 59_700, "반팔 티셔츠 외 1건", NOW);
        approved.requestConfirm("tgen_1", 59_700, NOW);
        approved.approve(1, "카드", NOW, NOW);
        return approved;
    }

    RequestRefundCommand command(UUID claimId, long amount) {
        return new RequestRefundCommand(claimId, payment.orderId(), UUID.randomUUID(), amount);
    }

    Refund only() {
        return refunds.rows.values().iterator().next();
    }

    @Test
    @DisplayName("클레임 승인을 받으면 REQUESTED 환불을 만든다. 같은 클레임이 다시 와도 하나다")
    void requests() {
        UUID claimId = UUID.randomUUID();

        request.execute(command(claimId, 19_900));
        request.execute(command(claimId, 19_900));

        assertThat(refunds.rows).hasSize(1);
        assertThat(only().status()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(outbox.events).isEmpty();
    }

    @Test
    @DisplayName("금액 0이면 토스를 부르지 않고 바로 RefundCompleted를 낸다")
    void zeroAmount() {
        request.execute(command(UUID.randomUUID(), 0));

        assertThat(only().status()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(outbox.types()).containsExactly(RefundCompleted.TYPE);
        assertThat(outbox.events.getFirst().aggregateId()).isEqualTo(payment.orderId().toString());
        assertThat(((RefundCompleted) outbox.events.getFirst().payload()).amount()).isZero();
        assertThat(payment.status()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    @DisplayName(
            "진행 중인 환불을 더해 남은 금액을 넘으면 REFUND_AMOUNT_EXCEEDED, 결제가 없으면 PAYMENT_NOT_REFUNDABLE로 실패한다")
    void rejects() {
        request.execute(command(UUID.randomUUID(), 39_800));
        request.execute(command(UUID.randomUUID(), 19_901));
        request.execute(
                new RequestRefundCommand(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1_000));

        assertThat(refunds.rows.values())
                .extracting(Refund::status, Refund::failureCode)
                .containsExactly(
                        tuple(RefundStatus.REQUESTED, null),
                        tuple(RefundStatus.FAILED, RefundFailed.REFUND_AMOUNT_EXCEEDED),
                        tuple(RefundStatus.FAILED, RefundFailed.PAYMENT_NOT_REFUNDABLE));
        assertThat(outbox.types()).containsExactly(RefundFailed.TYPE, RefundFailed.TYPE);
    }

    @Test
    @DisplayName("환불 작업이 토스 부분취소를 부르고, 완료되면 결제를 PARTIALLY_CANCELLED로 바꾸고 RefundCompleted를 낸다")
    void processes() {
        request.execute(command(UUID.randomUUID(), 19_900));
        Refund refund = only();
        gateway.refundResults.add(new Refunded("PARTIAL_CANCELED", "txn_refund_1"));

        process.execute(refund.id());
        process.execute(refund.id());

        assertThat(gateway.calls).containsExactly("refund tgen_1 19900 " + refund.id() + "-1");
        assertThat(refund.status()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(payment.status()).isEqualTo(PaymentStatus.PARTIALLY_CANCELLED);
        assertThat(payment.remainingAmount()).isEqualTo(39_800);
        assertThat(ledger.entries)
                .extracting(
                        PaymentLedgerEntry::kind,
                        PaymentLedgerEntry::outcome,
                        PaymentLedgerEntry::amount)
                .containsExactly(
                        tuple(
                                PaymentLedgerEntry.Kind.REFUND,
                                PaymentLedgerEntry.Outcome.CANCELLED,
                                19_900L));
        assertThat(outbox.types()).containsExactly(RefundCompleted.TYPE);
        assertThat((RefundCompleted) outbox.events.getFirst().payload())
                .isEqualTo(
                        new RefundCompleted(
                                payment.orderId(),
                                refund.id(),
                                refund.claimId(),
                                refund.sellerOrderId(),
                                19_900,
                                NOW));
    }

    @Test
    @DisplayName("남은 금액을 모두 환불하면 결제가 CANCELLED가 된다")
    void refundsAll() {
        request.execute(command(UUID.randomUUID(), 59_700));
        gateway.refundResults.add(new Refunded("PARTIAL_CANCELED", "txn_refund_1"));

        process.execute(only().id());

        assertThat(payment.status()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(payment.remainingAmount()).isZero();
    }

    @Test
    @DisplayName("결과를 알 수 없으면 REQUESTED로 두고 다음에 같은 멱등 키로 다시 부른다. 토스가 거절하면 RefundFailed를 낸다")
    void unknownThenRejected() {
        request.execute(command(UUID.randomUUID(), 19_900));
        Refund refund = only();
        gateway.refundResults.add(new Unknown("TIMEOUT"));
        gateway.refundResults.add(new CancelRejected("NOT_CANCELABLE_AMOUNT", "취소 불가"));

        process.execute(refund.id());
        assertThat(refund.status()).isEqualTo(RefundStatus.REQUESTED);
        process.execute(refund.id());

        assertThat(gateway.calls)
                .containsExactly(
                        "refund tgen_1 19900 " + refund.id() + "-1",
                        "refund tgen_1 19900 " + refund.id() + "-1");
        assertThat(refund.status()).isEqualTo(RefundStatus.FAILED);
        assertThat(refund.failureCode()).isEqualTo("NOT_CANCELABLE_AMOUNT");
        assertThat(payment.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(ledger.entries)
                .extracting(PaymentLedgerEntry::outcome)
                .containsExactly(
                        PaymentLedgerEntry.Outcome.UNKNOWN, PaymentLedgerEntry.Outcome.REJECTED);
        assertThat(outbox.types()).containsExactly(RefundFailed.TYPE);
    }
}
