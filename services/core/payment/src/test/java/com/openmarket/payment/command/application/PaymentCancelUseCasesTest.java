package com.openmarket.payment.command.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import com.openmarket.payment.command.application.PaymentGatewayPort.Approved;
import com.openmarket.payment.command.application.PaymentGatewayPort.CancelRejected;
import com.openmarket.payment.command.application.PaymentGatewayPort.Cancelled;
import com.openmarket.payment.command.application.PaymentGatewayPort.Rejected;
import com.openmarket.payment.command.application.PaymentGatewayPort.Unknown;
import com.openmarket.payment.command.domain.Payment;
import com.openmarket.payment.command.domain.PaymentCancelled;
import com.openmarket.payment.command.domain.PaymentCancelledException;
import com.openmarket.payment.command.domain.PaymentLedgerEntry;
import com.openmarket.payment.command.domain.PaymentStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

/** 결제 전 취소된 주문의 결제 무효화와 보상 취소(PAY-3, docs/architecture/payment.md "보상 취소"). */
class PaymentCancelUseCasesTest {

    static final Instant NOW = Instant.parse("2026-10-21T00:00:00Z");
    static final Approved DONE = new Approved("DONE", 59_700, "카드", NOW, "txn_1");
    static final Cancelled CANCELED = new Cancelled("CANCELED", "txn_cancel_1");

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

    final UUID orderId = UUID.randomUUID();
    final UUID buyerId = UUID.randomUUID();

    Payment ready() {
        prepare.execute(new PreparePaymentCommand(orderId, buyerId, 59_700, "반팔 티셔츠"));
        return payments.rows.values().iterator().next();
    }

    ConfirmPaymentCommand command(String paymentKey) {
        return new ConfirmPaymentCommand(buyerId, orderId, paymentKey, 59_700);
    }

    void orderCancelled() {
        requestCancel.execute(new RequestPaymentCancelCommand(orderId));
    }

    @Test
    @DisplayName("결제 전 취소된 주문의 READY 결제는 CANCELLED가 되고 승인을 막는다. 토스를 부르지 않고 이벤트도 없다")
    void cancelsReady() {
        Payment payment = ready();

        orderCancelled();
        orderCancelled();

        assertThat(payment.status()).isEqualTo(PaymentStatus.CANCELLED);
        assertThatThrownBy(() -> confirm.execute(command("tgen_1")))
                .isInstanceOf(PaymentCancelledException.class);
        assertThat(gateway.calls).isEmpty();
        assertThat(outbox.events).isEmpty();
    }

    @Test
    @DisplayName("결제가 없는 주문(예약 중에 취소)의 취소는 아무것도 하지 않는다")
    void noPayment() {
        orderCancelled();

        assertThat(payments.rows).isEmpty();
    }

    @Test
    @DisplayName(
            "승인 응답보다 주문 취소가 먼저 반영되면 409 PAYMENT_CANCELLED다. 승인된 결제는 토스 전액 취소 뒤 CANCELLED와 PaymentCancelled다")
    void compensatesLateApproval() {
        Payment payment = ready();
        gateway.duringConfirm = this::orderCancelled;
        gateway.confirmResults.add(DONE);

        assertThatThrownBy(() -> confirm.execute(command("tgen_1")))
                .isInstanceOf(PaymentCancelledException.class);
        assertThat(payment.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(outbox.types()).containsExactly("PaymentApproved");

        gateway.cancelResults.add(CANCELED);
        cancelApproved.execute(payment.id());
        cancelApproved.execute(payment.id());

        assertThat(payment.status()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(gateway.calls)
                .containsExactly(
                        "confirm tgen_1 59700 " + payment.id() + "-1",
                        "cancel tgen_1 " + payment.id() + "-cancel");
        assertThat(outbox.types()).containsExactly("PaymentApproved", "PaymentCancelled");
        assertThat(outbox.events.getLast().payload())
                .isEqualTo(
                        new PaymentCancelled(
                                orderId,
                                payment.id(),
                                59_700,
                                PaymentCancelled.Reason.ORDER_CANCELLED,
                                NOW));
        assertThat(ledger.entries)
                .extracting(PaymentLedgerEntry::kind, PaymentLedgerEntry::outcome)
                .containsExactly(
                        tuple(PaymentLedgerEntry.Kind.CONFIRM, PaymentLedgerEntry.Outcome.APPROVED),
                        tuple(
                                PaymentLedgerEntry.Kind.CANCEL,
                                PaymentLedgerEntry.Outcome.CANCELLED));
    }

    @Test
    @DisplayName("승인을 기다리는 동안 취소된 주문의 결제가 재조회로 승인되면 토스 취소 대상이 된다")
    void compensatesAfterReconcile() {
        Payment payment = ready();
        gateway.confirmResults.add(new Unknown("TIMEOUT"));
        gateway.findResults.add(new Unknown("TIMEOUT"));
        confirm.execute(command("tgen_1"));
        orderCancelled();

        gateway.findResults.add(DONE);
        reconcile.execute(payment.id());
        assertThat(payment.needsGatewayCancel()).isTrue();

        gateway.cancelResults.add(CANCELED);
        cancelApproved.execute(payment.id());
        assertThat(payment.status()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(outbox.types()).containsExactly("PaymentApproved", "PaymentCancelled");
    }

    @Test
    @DisplayName("승인을 기다리는 동안 취소된 주문의 결제가 거절되면 CANCELLED이고 PaymentFailed를 내지 않는다")
    void rejectedAfterCancel() {
        Payment payment = ready();
        gateway.duringConfirm = this::orderCancelled;
        gateway.confirmResults.add(new Rejected("REJECT_CARD_PAYMENT", "잔액이 부족합니다."));

        assertThatThrownBy(() -> confirm.execute(command("tgen_1")))
                .isInstanceOf(PaymentCancelledException.class);

        assertThat(payment.status()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(outbox.events).isEmpty();
    }

    @Test
    @DisplayName("토스 취소 결과를 알 수 없으면 APPROVED로 두고 다음에 같은 멱등 키로 다시 취소한다")
    void retriesUnknownCancel() {
        Payment payment = approvedWithCancelRequest();

        gateway.cancelResults.add(new Unknown("TIMEOUT"));
        cancelApproved.execute(payment.id());
        assertThat(payment.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.needsGatewayCancel()).isTrue();

        gateway.cancelResults.add(CANCELED);
        cancelApproved.execute(payment.id());
        assertThat(payment.status()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(gateway.calls)
                .filteredOn(call -> call.startsWith("cancel"))
                .containsExactly(
                        "cancel tgen_1 " + payment.id() + "-cancel",
                        "cancel tgen_1 " + payment.id() + "-cancel");
        assertThat(ledger.entries)
                .filteredOn(e -> e.kind() == PaymentLedgerEntry.Kind.CANCEL)
                .extracting(PaymentLedgerEntry::outcome, PaymentLedgerEntry::pgCode)
                .containsExactly(
                        tuple(PaymentLedgerEntry.Outcome.UNKNOWN, "TIMEOUT"),
                        tuple(PaymentLedgerEntry.Outcome.CANCELLED, "CANCELED"));
    }

    @Test
    @DisplayName("토스가 취소를 거절하면 실패 코드를 남기고 더는 부르지 않는다")
    void stopsOnRejectedCancel() {
        Payment payment = approvedWithCancelRequest();

        gateway.cancelResults.add(new CancelRejected("NOT_CANCELABLE_PAYMENT", "취소할 수 없는 결제입니다."));
        cancelApproved.execute(payment.id());
        cancelApproved.execute(payment.id());

        assertThat(payment.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.cancelFailureCode()).isEqualTo("NOT_CANCELABLE_PAYMENT");
        assertThat(gateway.calls).filteredOn(call -> call.startsWith("cancel")).hasSize(1);
        assertThat(outbox.types()).containsExactly("PaymentApproved");
    }

    @Test
    @DisplayName("취소 요청이 없는 승인 결제는 토스 취소를 부르지 않는다")
    void skipsWithoutRequest() {
        Payment payment = ready();
        gateway.confirmResults.add(DONE);
        confirm.execute(command("tgen_1"));

        cancelApproved.execute(payment.id());

        assertThat(gateway.calls).filteredOn(call -> call.startsWith("cancel")).isEmpty();
        assertThat(payment.status()).isEqualTo(PaymentStatus.APPROVED);
    }

    private Payment approvedWithCancelRequest() {
        Payment payment = ready();
        gateway.confirmResults.add(DONE);
        confirm.execute(command("tgen_1"));
        orderCancelled();
        return payment;
    }
}
