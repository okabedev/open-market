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
}
