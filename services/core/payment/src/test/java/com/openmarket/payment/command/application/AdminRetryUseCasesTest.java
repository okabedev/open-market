package com.openmarket.payment.command.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmarket.common.core.audit.AuditEntry;
import com.openmarket.payment.command.application.PaymentGatewayPort.CancelRejected;
import com.openmarket.payment.command.application.PaymentGatewayPort.Cancelled;
import com.openmarket.payment.command.application.PaymentGatewayPort.Refunded;
import com.openmarket.payment.command.domain.Payment;
import com.openmarket.payment.command.domain.PaymentStatus;
import com.openmarket.payment.command.domain.Refund;
import com.openmarket.payment.command.domain.RefundStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

/** 환불·보상 취소의 관리자 재시도(docs/architecture/payment.md "관리자 재시도"). */
class AdminRetryUseCasesTest {

    static final Instant NOW = Instant.parse("2026-10-22T00:00:00Z");

    final PaymentFakes.Payments payments = new PaymentFakes.Payments();
    final PaymentFakes.Refunds refunds = new PaymentFakes.Refunds();
    final PaymentFakes.Ledger ledger = new PaymentFakes.Ledger();
    final PaymentFakes.Gateway gateway = new PaymentFakes.Gateway();
    final PaymentFakes.RecordingOutbox outbox = new PaymentFakes.RecordingOutbox();
    final List<AuditEntry> audits = new ArrayList<>();
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
    final RetryRefundUseCase retryRefund =
            new RetryRefundUseCase(payments, refunds, audits::add, clock);
    final RequestPaymentCancelUseCase requestCancel =
            new RequestPaymentCancelUseCase(payments, clock);
    final CancelApprovedPaymentUseCase cancelApproved =
            new CancelApprovedPaymentUseCase(
                    payments,
                    ledger,
                    gateway,
                    outbox,
                    TransactionOperations.withoutTransaction(),
                    clock);
    final RetryPaymentCancelUseCase retryCancel =
            new RetryPaymentCancelUseCase(payments, audits::add, clock);

    final UUID adminId = UUID.randomUUID();
    final Payment payment = payments.save(approved());

    Payment approved() {
        Payment approved = Payment.ready(UUID.randomUUID(), UUID.randomUUID(), 59_700, "주문", NOW);
        approved.requestConfirm("tgen_1", 59_700, NOW);
        approved.approve(1, "카드", NOW, NOW);
        return approved;
    }

    Refund refund(long amount) {
        request.execute(
                new RequestRefundCommand(
                        UUID.randomUUID(), payment.orderId(), UUID.randomUUID(), amount));
        return refunds.rows.values().stream().reduce((first, second) -> second).orElseThrow();
    }

    @Test
    @DisplayName("토스가 거절한 환불을 다시 시도하면 시도 번호를 올린 새 멱등 키로 다시 부른다. 감사 로그를 남긴다")
    void retriesRefund() {
        Refund refund = refund(19_900);
        gateway.refundResults.add(new CancelRejected("NOT_CANCELABLE_AMOUNT", "거절"));
        process.execute(refund.id());
        assertThat(refund.status()).isEqualTo(RefundStatus.FAILED);

        retryRefund.execute(new RetryRefundCommand(adminId, refund.id()));
        retryRefund.execute(new RetryRefundCommand(adminId, refund.id()));

        assertThat(refund.status()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(refund.failureCode()).isNull();
        assertThat(audits)
                .singleElement()
                .satisfies(
                        entry -> {
                            assertThat(entry.action()).isEqualTo("REFUND_RETRIED");
                            assertThat(entry.actorId()).isEqualTo(adminId.toString());
                            assertThat(entry.detail()).isEqualTo("NOT_CANCELABLE_AMOUNT");
                        });

        gateway.refundResults.add(new Refunded("PARTIAL_CANCELED", "txn_2"));
        process.execute(refund.id());

        assertThat(refund.status()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(gateway.calls.getLast()).endsWith(refund.id() + "-2");
        assertThatThrownBy(() -> retryRefund.execute(new RetryRefundCommand(adminId, refund.id())))
                .isInstanceOfSatisfying(
                        com.openmarket.common.core.error.BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo("PAYMENT_NOT_RETRYABLE"));
    }

    @Test
    @DisplayName("다시 확인해도 환불할 수 없으면 409이고 FAILED로 남는다")
    void retryStillExceeds() {
        Refund refund = refund(60_000);
        assertThat(refund.failureCode()).isEqualTo("REFUND_AMOUNT_EXCEEDED");

        assertThatThrownBy(() -> retryRefund.execute(new RetryRefundCommand(adminId, refund.id())))
                .isInstanceOfSatisfying(
                        com.openmarket.common.core.error.BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo("PAYMENT_REFUND_AMOUNT_EXCEEDED"));
        assertThat(refund.status()).isEqualTo(RefundStatus.FAILED);
        assertThat(audits).isEmpty();
    }

    @Test
    @DisplayName("없는 환불은 404 PAYMENT_REFUND_NOT_FOUND다")
    void refundNotFound() {
        assertThatThrownBy(
                        () ->
                                retryRefund.execute(
                                        new RetryRefundCommand(adminId, UUID.randomUUID())))
                .isInstanceOfSatisfying(
                        com.openmarket.common.core.error.BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo("PAYMENT_REFUND_NOT_FOUND"));
    }

    @Test
    @DisplayName("토스가 거절한 보상 취소를 다시 시도하면 시도 번호가 든 새 멱등 키로 다시 부른다")
    void retriesCancel() {
        requestCancel.execute(new RequestPaymentCancelCommand(payment.orderId()));
        gateway.cancelResults.add(new CancelRejected("NOT_CANCELABLE_PAYMENT", "거절"));
        cancelApproved.execute(payment.id());
        assertThat(payment.cancelFailureCode()).isEqualTo("NOT_CANCELABLE_PAYMENT");

        retryCancel.execute(new RetryPaymentCancelCommand(adminId, payment.id()));
        retryCancel.execute(new RetryPaymentCancelCommand(adminId, payment.id()));

        assertThat(payment.cancelFailureCode()).isNull();
        assertThat(payment.needsGatewayCancel()).isTrue();
        assertThat(audits).extracting(AuditEntry::action).containsExactly("PAYMENT_CANCEL_RETRIED");

        gateway.cancelResults.add(new Cancelled("CANCELED", "txn_cancel_2"));
        cancelApproved.execute(payment.id());

        assertThat(payment.status()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(gateway.calls.getFirst()).endsWith(payment.id() + "-cancel");
        assertThat(gateway.calls.getLast()).endsWith(payment.id() + "-cancel-2");
    }

    @Test
    @DisplayName("취소 요청이 없는 결제는 다시 시도할 수 없다(409)")
    void cancelNotRetryable() {
        assertThatThrownBy(
                        () ->
                                retryCancel.execute(
                                        new RetryPaymentCancelCommand(adminId, payment.id())))
                .isInstanceOfSatisfying(
                        com.openmarket.common.core.error.BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo("PAYMENT_NOT_RETRYABLE"));
    }
}
