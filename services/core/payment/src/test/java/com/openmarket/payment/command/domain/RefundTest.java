package com.openmarket.payment.command.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RefundTest {

    static final Instant NOW = Instant.parse("2026-10-21T00:00:00Z");

    final UUID claimId = UUID.randomUUID();
    final UUID sellerOrderId = UUID.randomUUID();

    Payment approved(long amount) {
        Payment payment = Payment.ready(UUID.randomUUID(), UUID.randomUUID(), amount, "주문", NOW);
        payment.requestConfirm("tgen_1", amount, NOW);
        payment.approve(1, "카드", NOW, NOW);
        return payment;
    }

    @Test
    @DisplayName("환불은 REQUESTED로 만들고 토스 멱등 키는 환불 ID와 시도 번호다")
    void requested() {
        Payment payment = approved(59_700);

        Refund refund = Refund.requested(payment, claimId, sellerOrderId, 19_900, NOW);

        assertThat(refund.status()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(refund.paymentId()).isEqualTo(payment.id());
        assertThat(refund.orderId()).isEqualTo(payment.orderId());
        assertThat(refund.amount()).isEqualTo(19_900);
        assertThat(refund.gatewayIdempotencyKey()).isEqualTo(refund.id() + "-1");
    }

    @Test
    @DisplayName("금액 0인 환불은 바로 COMPLETED다")
    void zeroAmount() {
        Refund refund = Refund.requested(approved(59_700), claimId, sellerOrderId, 0, NOW);

        assertThat(refund.status()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(refund.completedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("환불할 수 없으면 실패 코드와 함께 FAILED로 만든다")
    void rejected() {
        Refund refund =
                Refund.rejected(
                        UUID.randomUUID(),
                        claimId,
                        sellerOrderId,
                        19_900,
                        "PAYMENT_NOT_REFUNDABLE",
                        NOW);

        assertThat(refund.status()).isEqualTo(RefundStatus.FAILED);
        assertThat(refund.failureCode()).isEqualTo("PAYMENT_NOT_REFUNDABLE");
        assertThat(refund.paymentId()).isNull();
    }

    @Test
    @DisplayName("REQUESTED 환불만 완료하거나 실패한다. 다시 하면 ALREADY다")
    void transitions() {
        Refund completed = Refund.requested(approved(59_700), claimId, sellerOrderId, 100, NOW);
        assertThat(completed.complete(NOW)).isEqualTo(Refund.Transition.APPLIED);
        assertThat(completed.complete(NOW)).isEqualTo(Refund.Transition.ALREADY);
        assertThat(completed.fail("NOT_CANCELABLE_AMOUNT", NOW))
                .isEqualTo(Refund.Transition.ALREADY);

        Refund failed = Refund.requested(approved(59_700), claimId, sellerOrderId, 100, NOW);
        assertThat(failed.fail("NOT_CANCELABLE_AMOUNT", NOW)).isEqualTo(Refund.Transition.APPLIED);
        assertThat(failed.status()).isEqualTo(RefundStatus.FAILED);
        assertThat(failed.failureCode()).isEqualTo("NOT_CANCELABLE_AMOUNT");
    }

    @Test
    @DisplayName("결제는 환불한 금액을 세어 남은 금액이 있으면 PARTIALLY_CANCELLED, 없으면 CANCELLED가 된다")
    void paymentRefunded() {
        Payment payment = approved(59_700);
        assertThat(payment.isRefundable()).isTrue();

        payment.refunded(19_900, NOW);
        assertThat(payment.status()).isEqualTo(PaymentStatus.PARTIALLY_CANCELLED);
        assertThat(payment.remainingAmount()).isEqualTo(39_800);
        assertThat(payment.isRefundable()).isTrue();

        payment.refunded(39_800, NOW);
        assertThat(payment.status()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(payment.remainingAmount()).isZero();
        assertThat(payment.cancelledAt()).isEqualTo(NOW);
        assertThat(payment.isRefundable()).isFalse();
    }

    @Test
    @DisplayName("승인 전 결제나 남은 금액보다 큰 환불은 결제에 반영하지 않는다")
    void paymentNotRefundable() {
        Payment ready = Payment.ready(UUID.randomUUID(), UUID.randomUUID(), 1_000, "주문", NOW);
        assertThat(ready.isRefundable()).isFalse();
        assertThatThrownBy(() -> ready.refunded(100, NOW))
                .isInstanceOf(IllegalStateException.class);

        Payment payment = approved(1_000);
        assertThatThrownBy(() -> payment.refunded(1_001, NOW))
                .isInstanceOf(IllegalStateException.class);
        assertThat(payment.status()).isEqualTo(PaymentStatus.APPROVED);
    }
}
