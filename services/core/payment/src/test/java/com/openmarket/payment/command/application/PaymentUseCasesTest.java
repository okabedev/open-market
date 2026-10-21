package com.openmarket.payment.command.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmarket.payment.command.application.PaymentGatewayPort.Approved;
import com.openmarket.payment.command.application.PaymentGatewayPort.Pending;
import com.openmarket.payment.command.application.PaymentGatewayPort.Rejected;
import com.openmarket.payment.command.application.PaymentGatewayPort.Unknown;
import com.openmarket.payment.command.domain.Payment;
import com.openmarket.payment.command.domain.PaymentAmountMismatchException;
import com.openmarket.payment.command.domain.PaymentApproved;
import com.openmarket.payment.command.domain.PaymentFailed;
import com.openmarket.payment.command.domain.PaymentLedgerEntry;
import com.openmarket.payment.command.domain.PaymentNotFoundException;
import com.openmarket.payment.command.domain.PaymentRejectedException;
import com.openmarket.payment.command.domain.PaymentStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

class PaymentUseCasesTest {

    static final Instant NOW = Instant.parse("2026-10-21T00:00:00Z");
    static final Approved DONE = new Approved("DONE", 59_700, "카드", NOW, "txn_1");

    final PaymentFakes.Payments payments = new PaymentFakes.Payments();
    final PaymentFakes.Ledger ledger = new PaymentFakes.Ledger();
    final PaymentFakes.Gateway gateway = new PaymentFakes.Gateway();
    final PaymentFakes.RecordingOutbox outbox = new PaymentFakes.RecordingOutbox();
    final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    final ApplyGatewayResultUseCase apply =
            new ApplyGatewayResultUseCase(payments, ledger, outbox, clock);
    final ConfirmPaymentUseCase confirm =
            new ConfirmPaymentUseCase(
                    payments, gateway, apply, TransactionOperations.withoutTransaction(), clock);
    final ReconcilePaymentUseCase reconcile =
            new ReconcilePaymentUseCase(
                    payments, gateway, apply, TransactionOperations.withoutTransaction());
    final PreparePaymentUseCase prepare = new PreparePaymentUseCase(payments, clock);

    final UUID orderId = UUID.randomUUID();
    final UUID buyerId = UUID.randomUUID();

    Payment ready() {
        prepare.execute(new PreparePaymentCommand(orderId, buyerId, 59_700, "반팔 티셔츠"));
        return payments.rows.values().iterator().next();
    }

    ConfirmPaymentCommand command(String paymentKey, long amount) {
        return new ConfirmPaymentCommand(buyerId, orderId, paymentKey, amount);
    }

    @Test
    @DisplayName("결제 준비는 주문마다 한 번만 READY 결제를 만든다")
    void prepare() {
        ready();
        prepare.execute(new PreparePaymentCommand(orderId, buyerId, 59_700, "반팔 티셔츠"));

        assertThat(payments.rows).hasSize(1);
        assertThat(payments.rows.values().iterator().next().status())
                .isEqualTo(PaymentStatus.READY);
    }

    @Test
    @DisplayName("승인되면 APPROVED가 되고 PaymentApproved를 내며 원장에 남긴다")
    void approves() {
        Payment payment = ready();
        gateway.confirmResults.add(DONE);

        ConfirmPaymentUseCase.Result result = confirm.execute(command("tgen_1", 59_700));

        assertThat(result.status()).isEqualTo(ConfirmPaymentUseCase.Status.APPROVED);
        assertThat(result.method()).isEqualTo("카드");
        assertThat(result.paymentId()).isEqualTo(payment.id());
        assertThat(gateway.calls).containsExactly("confirm tgen_1 59700 " + payment.id() + "-1");
        assertThat(payment.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(outbox.events)
                .singleElement()
                .satisfies(
                        e -> {
                            assertThat(e.aggregateType()).isEqualTo("payment.payment");
                            assertThat(e.aggregateId()).isEqualTo(orderId.toString());
                            assertThat(e.payload())
                                    .isEqualTo(
                                            new PaymentApproved(
                                                    orderId, payment.id(), 59_700, "카드", NOW));
                        });
        assertThat(ledger.entries)
                .extracting(PaymentLedgerEntry::kind, PaymentLedgerEntry::outcome)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                PaymentLedgerEntry.Kind.CONFIRM,
                                PaymentLedgerEntry.Outcome.APPROVED));
    }

    @Test
    @DisplayName("거절되면 FAILED가 되고 PaymentFailed를 내며 토스 오류를 알린다. 새 결제 키로 다시 승인할 수 있다")
    void rejects() {
        Payment payment = ready();
        gateway.confirmResults.add(
                new Rejected("REJECT_CARD_PAYMENT", "한도초과 혹은 잔액부족으로 결제에 실패했습니다."));

        assertThatThrownBy(() -> confirm.execute(command("tgen_1", 59_700)))
                .isInstanceOfSatisfying(
                        PaymentRejectedException.class,
                        e ->
                                assertThat(e.getProperties())
                                        .containsEntry("pgCode", "REJECT_CARD_PAYMENT"));
        assertThat(payment.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(outbox.events)
                .singleElement()
                .extracting(PaymentFakes.Appended::payload)
                .isEqualTo(new PaymentFailed(orderId, payment.id(), "REJECT_CARD_PAYMENT"));

        gateway.confirmResults.add(DONE);
        assertThat(confirm.execute(command("tgen_2", 59_700)).status())
                .isEqualTo(ConfirmPaymentUseCase.Status.APPROVED);
        assertThat(gateway.calls.getLast()).endsWith(payment.id() + "-2");
    }

    @Test
    @DisplayName("승인 응답으로 정하지 못하면 조회로 정한다")
    void queriesWhenUnknown() {
        Payment payment = ready();
        gateway.confirmResults.add(new Unknown("TIMEOUT"));
        gateway.findResults.add(DONE);

        assertThat(confirm.execute(command("tgen_1", 59_700)).status())
                .isEqualTo(ConfirmPaymentUseCase.Status.APPROVED);
        assertThat(gateway.calls)
                .containsExactly("confirm tgen_1 59700 " + payment.id() + "-1", "find tgen_1");
        assertThat(ledger.entries)
                .extracting(PaymentLedgerEntry::kind, PaymentLedgerEntry::pgCode)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                PaymentLedgerEntry.Kind.CONFIRM, "TIMEOUT"),
                        org.assertj.core.groups.Tuple.tuple(PaymentLedgerEntry.Kind.QUERY, "DONE"));
    }

    @Test
    @DisplayName("조회로도 정하지 못하면 IN_PROGRESS로 두고, 같은 결제 키로 다시 와도 토스를 부르지 않는다")
    void staysInProgress() {
        Payment payment = ready();
        gateway.confirmResults.add(new Unknown("TIMEOUT"));
        gateway.findResults.add(new Pending("IN_PROGRESS"));

        assertThat(confirm.execute(command("tgen_1", 59_700)).status())
                .isEqualTo(ConfirmPaymentUseCase.Status.IN_PROGRESS);
        assertThat(confirm.execute(command("tgen_1", 59_700)).status())
                .isEqualTo(ConfirmPaymentUseCase.Status.IN_PROGRESS);

        assertThat(payment.status()).isEqualTo(PaymentStatus.IN_PROGRESS);
        assertThat(gateway.calls).hasSize(2);
        assertThat(outbox.events).isEmpty();
    }

    @Test
    @DisplayName("금액이 다르거나 다른 회원의 결제면 토스를 부르지 않는다")
    void rejectsBeforeGateway() {
        ready();

        assertThatThrownBy(() -> confirm.execute(command("tgen_1", 1_000)))
                .isInstanceOf(PaymentAmountMismatchException.class);
        assertThatThrownBy(
                        () ->
                                confirm.execute(
                                        new ConfirmPaymentCommand(
                                                UUID.randomUUID(), orderId, "tgen_1", 59_700)))
                .isInstanceOf(PaymentNotFoundException.class);
        assertThatThrownBy(
                        () ->
                                confirm.execute(
                                        new ConfirmPaymentCommand(
                                                buyerId, UUID.randomUUID(), "tgen_1", 59_700)))
                .isInstanceOf(PaymentNotFoundException.class);
        assertThat(gateway.calls).isEmpty();
    }

    @Test
    @DisplayName("재조회는 IN_PROGRESS 결제를 조회해 정하고, 이미 정해진 결제는 조회하지 않는다")
    void reconciles() {
        Payment payment = ready();
        gateway.confirmResults.add(new Unknown("TIMEOUT"));
        gateway.findResults.add(new Unknown("TIMEOUT"));
        confirm.execute(command("tgen_1", 59_700));

        gateway.findResults.add(new Rejected("EXPIRED", "결제가 만료되었습니다."));
        reconcile.execute(payment.id());
        reconcile.execute(payment.id());

        assertThat(payment.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.failureCode()).isEqualTo("EXPIRED");
        assertThat(gateway.calls)
                .containsExactly(
                        "confirm tgen_1 59700 " + payment.id() + "-1",
                        "find tgen_1",
                        "find tgen_1");
        assertThat(outbox.types()).containsExactly("PaymentFailed");
    }
}
