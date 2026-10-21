package com.openmarket.payment.command.application;

import com.openmarket.common.messaging.outbox.Outbox;
import com.openmarket.payment.command.domain.Payment;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 승인된 클레임의 환불을 만든다(docs/architecture/payment.md "환불 요청"). 결제 행을 잠그고 남은 금액을 확인한다. 토스는 부르지 않는다. 금액
 * 0이면 바로 {@code RefundCompleted}, 환불할 수 없으면 {@code RefundFailed}를 낸다. 같은 클레임의 환불이 있으면 아무것도 하지 않는다.
 */
@Service
public class RequestRefundUseCase {

    private static final Logger log = LoggerFactory.getLogger(RequestRefundUseCase.class);

    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final Outbox outbox;
    private final Clock clock;

    public RequestRefundUseCase(
            PaymentRepository payments, RefundRepository refunds, Outbox outbox, Clock clock) {
        this.payments = payments;
        this.refunds = refunds;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public void execute(RequestRefundCommand command) {
        if (refunds.existsByClaimId(command.claimId())) {
            return;
        }
        Instant now = clock.instant();
        Optional<Payment> found = payments.findForUpdateByOrderId(command.orderId());
        Refund refund;
        if (found.isEmpty() || !found.get().isRefundable()) {
            refund = rejected(command, RefundFailed.PAYMENT_NOT_REFUNDABLE, now);
        } else {
            Payment payment = found.get();
            long available =
                    payment.remainingAmount() - refunds.sumRequestedAmountByPaymentId(payment.id());
            refund =
                    command.amount() > available
                            ? rejected(command, RefundFailed.REFUND_AMOUNT_EXCEEDED, now)
                            : Refund.requested(
                                    payment,
                                    command.claimId(),
                                    command.sellerOrderId(),
                                    command.amount(),
                                    now);
        }
        refunds.save(refund);
        if (refund.status() == RefundStatus.COMPLETED) {
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
                            Objects.requireNonNull(refund.completedAt())));
        } else if (refund.status() == RefundStatus.FAILED) {
            log.error(
                    "환불할 수 없다. 관리자가 확인해야 한다: claimId={}, orderId={}, code={}",
                    refund.claimId(),
                    refund.orderId(),
                    refund.failureCode());
            outbox.append(
                    ApplyGatewayResultUseCase.TOPIC,
                    refund.orderId().toString(),
                    RefundFailed.TYPE,
                    RefundFailed.of(refund));
        }
    }

    private static Refund rejected(RequestRefundCommand command, String code, Instant now) {
        return Refund.rejected(
                command.orderId(),
                command.claimId(),
                command.sellerOrderId(),
                command.amount(),
                code,
                now);
    }
}
