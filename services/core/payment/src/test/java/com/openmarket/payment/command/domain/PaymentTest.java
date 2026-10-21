package com.openmarket.payment.command.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PaymentTest {

    static final Instant NOW = Instant.parse("2026-10-21T00:00:00Z");

    final UUID orderId = UUID.randomUUID();
    final UUID buyerId = UUID.randomUUID();

    Payment ready() {
        return Payment.ready(orderId, buyerId, 59_700, "반팔 티셔츠 외 1건", NOW);
    }

    @Test
    @DisplayName("결제는 READY로 만들고 주문의 금액을 고정한다")
    void ready_() {
        Payment payment = ready();

        assertThat(payment.status()).isEqualTo(PaymentStatus.READY);
        assertThat(payment.orderId()).isEqualTo(orderId);
        assertThat(payment.amount()).isEqualTo(59_700);
        assertThat(payment.isOwnedBy(buyerId)).isTrue();
        assertThat(payment.isOwnedBy(UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("승인을 요청하면 IN_PROGRESS가 되고 시도 번호가 토스 멱등 키에 들어간다")
    void requestConfirm() {
        Payment payment = ready();

        assertThat(payment.requestConfirm("tgen_1", 59_700, NOW))
                .isEqualTo(Payment.ConfirmRequest.STARTED);

        assertThat(payment.status()).isEqualTo(PaymentStatus.IN_PROGRESS);
        assertThat(payment.paymentKey()).isEqualTo("tgen_1");
        assertThat(payment.confirmAttempt()).isEqualTo(1);
        assertThat(payment.requestedAt()).isEqualTo(NOW);
        assertThat(payment.gatewayIdempotencyKey()).isEqualTo(payment.id() + "-1");
    }

    @Test
    @DisplayName("금액이 다르면 승인을 요청하지 않고 READY로 남는다")
    void amountMismatch() {
        Payment payment = ready();

        assertThatThrownBy(() -> payment.requestConfirm("tgen_1", 59_699, NOW))
                .isInstanceOf(PaymentAmountMismatchException.class);
        assertThat(payment.status()).isEqualTo(PaymentStatus.READY);
        assertThat(payment.confirmAttempt()).isZero();
    }

    @Test
    @DisplayName("승인을 기다리거나 승인된 결제는 같은 결제 키면 지금 상태를 알리고, 다른 키면 거부한다")
    void requestConfirmAgain() {
        Payment payment = ready();
        payment.requestConfirm("tgen_1", 59_700, NOW);

        assertThat(payment.requestConfirm("tgen_1", 59_700, NOW))
                .isEqualTo(Payment.ConfirmRequest.PENDING);
        assertThatThrownBy(() -> payment.requestConfirm("tgen_2", 59_700, NOW))
                .isInstanceOf(PaymentNotConfirmableException.class);

        payment.approve(1, "카드", NOW, NOW);
        assertThat(payment.requestConfirm("tgen_1", 59_700, NOW))
                .isEqualTo(Payment.ConfirmRequest.APPROVED);
        assertThatThrownBy(() -> payment.requestConfirm("tgen_2", 59_700, NOW))
                .isInstanceOf(PaymentNotConfirmableException.class);
        assertThat(payment.confirmAttempt()).isEqualTo(1);
    }

    @Test
    @DisplayName("승인 결과는 같은 시도에만 반영한다. 이미 반영됐으면 그대로이고, 지난 시도의 결과는 버린다")
    void approve() {
        Payment payment = ready();
        payment.requestConfirm("tgen_1", 59_700, NOW);

        assertThat(payment.approve(1, "카드", NOW, NOW)).isEqualTo(Payment.Transition.APPLIED);
        assertThat(payment.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.method()).isEqualTo("카드");
        assertThat(payment.approvedAt()).isEqualTo(NOW);
        assertThat(payment.approve(1, "카드", NOW, NOW)).isEqualTo(Payment.Transition.ALREADY);
        assertThat(payment.fail(1, "REJECT_CARD_PAYMENT", NOW)).isEqualTo(Payment.Transition.STALE);
        assertThat(payment.status()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    @DisplayName("거절된 결제는 FAILED가 되고, 새 결제 키로 다시 승인을 요청하면 다음 시도가 된다")
    void failAndRetry() {
        Payment payment = ready();
        payment.requestConfirm("tgen_1", 59_700, NOW);

        assertThat(payment.fail(1, "REJECT_CARD_PAYMENT", NOW))
                .isEqualTo(Payment.Transition.APPLIED);
        assertThat(payment.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.failureCode()).isEqualTo("REJECT_CARD_PAYMENT");
        assertThat(payment.fail(1, "REJECT_CARD_PAYMENT", NOW))
                .isEqualTo(Payment.Transition.ALREADY);

        assertThat(payment.requestConfirm("tgen_2", 59_700, NOW))
                .isEqualTo(Payment.ConfirmRequest.STARTED);
        assertThat(payment.confirmAttempt()).isEqualTo(2);
        assertThat(payment.failureCode()).isNull();
        assertThat(payment.approve(1, "카드", NOW, NOW)).isEqualTo(Payment.Transition.STALE);
        assertThat(payment.status()).isEqualTo(PaymentStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("주문이 취소되면 돈이 빠져나가지 않은 결제(READY, FAILED)는 바로 CANCELLED다. 다시 와도 그대로다")
    void cancelWithoutMoney() {
        Payment ready = ready();
        assertThat(ready.requestCancel(NOW)).isEqualTo(Payment.CancelRequest.CANCELLED);
        assertThat(ready.status()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(ready.cancelledAt()).isEqualTo(NOW);
        assertThat(ready.requestCancel(NOW)).isEqualTo(Payment.CancelRequest.ALREADY);

        Payment failed = ready();
        failed.requestConfirm("tgen_1", 59_700, NOW);
        failed.fail(1, "REJECT_CARD_PAYMENT", NOW);
        assertThat(failed.requestCancel(NOW)).isEqualTo(Payment.CancelRequest.CANCELLED);
        assertThat(failed.status()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(failed.needsGatewayCancel()).isFalse();
    }

    @Test
    @DisplayName("취소된 결제와 취소 요청이 있는 결제는 승인을 요청할 수 없다(같은 결제 키여도)")
    void confirmAfterCancel() {
        Payment ready = ready();
        ready.requestCancel(NOW);
        assertThatThrownBy(() -> ready.requestConfirm("tgen_1", 59_700, NOW))
                .isInstanceOf(PaymentCancelledException.class);

        Payment inProgress = ready();
        inProgress.requestConfirm("tgen_1", 59_700, NOW);
        inProgress.requestCancel(NOW);
        assertThatThrownBy(() -> inProgress.requestConfirm("tgen_1", 59_700, NOW))
                .isInstanceOf(PaymentCancelledException.class);
        assertThat(inProgress.confirmAttempt()).isEqualTo(1);
    }

    @Test
    @DisplayName("승인을 기다리는 결제는 취소 요청만 남긴다. 승인되면 토스 취소가 필요하고, 거절되면 CANCELLED다")
    void cancelWhileInProgress() {
        Payment approved = ready();
        approved.requestConfirm("tgen_1", 59_700, NOW);
        assertThat(approved.requestCancel(NOW)).isEqualTo(Payment.CancelRequest.REQUESTED);
        assertThat(approved.requestCancel(NOW)).isEqualTo(Payment.CancelRequest.ALREADY);
        assertThat(approved.status()).isEqualTo(PaymentStatus.IN_PROGRESS);
        assertThat(approved.cancelRequestedAt()).isEqualTo(NOW);
        assertThat(approved.needsGatewayCancel()).isFalse();

        assertThat(approved.approve(1, "카드", NOW, NOW)).isEqualTo(Payment.Transition.APPLIED);
        assertThat(approved.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(approved.needsGatewayCancel()).isTrue();

        Payment rejected = ready();
        rejected.requestConfirm("tgen_1", 59_700, NOW);
        rejected.requestCancel(NOW);
        assertThat(rejected.fail(1, "REJECT_CARD_PAYMENT", NOW))
                .isEqualTo(Payment.Transition.APPLIED);
        assertThat(rejected.status()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(rejected.failureCode()).isEqualTo("REJECT_CARD_PAYMENT");
        assertThat(rejected.needsGatewayCancel()).isFalse();
    }

    @Test
    @DisplayName("승인된 결제는 취소 요청을 남기고, 토스 취소가 끝나면 CANCELLED다. 토스 취소의 멱등 키는 결제마다 하나다")
    void cancelApproved() {
        Payment payment = ready();
        payment.requestConfirm("tgen_1", 59_700, NOW);
        payment.approve(1, "카드", NOW, NOW);

        assertThat(payment.cancelled(NOW)).isEqualTo(Payment.Transition.STALE);
        assertThat(payment.requestCancel(NOW)).isEqualTo(Payment.CancelRequest.REQUESTED);
        assertThat(payment.needsGatewayCancel()).isTrue();
        assertThat(payment.gatewayCancelIdempotencyKey()).isEqualTo(payment.id() + "-cancel");

        Instant later = NOW.plusSeconds(60);
        assertThat(payment.cancelled(later)).isEqualTo(Payment.Transition.APPLIED);
        assertThat(payment.status()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(payment.cancelledAt()).isEqualTo(later);
        assertThat(payment.needsGatewayCancel()).isFalse();
        assertThat(payment.cancelled(later)).isEqualTo(Payment.Transition.ALREADY);
    }

    @Test
    @DisplayName("토스가 취소를 거절하면 코드를 남기고 APPROVED로 둔다. 더는 자동으로 취소하지 않는다")
    void cancelRejected() {
        Payment payment = ready();
        payment.requestConfirm("tgen_1", 59_700, NOW);
        payment.approve(1, "카드", NOW, NOW);
        payment.requestCancel(NOW);

        assertThat(payment.cancelRejected("NOT_CANCELABLE_PAYMENT", NOW))
                .isEqualTo(Payment.Transition.APPLIED);

        assertThat(payment.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.cancelFailureCode()).isEqualTo("NOT_CANCELABLE_PAYMENT");
        assertThat(payment.needsGatewayCancel()).isFalse();
    }
}
