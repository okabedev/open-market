package com.openmarket.payment.command.application;

import com.openmarket.payment.command.domain.Payment;
import com.openmarket.payment.command.domain.PaymentRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 전에 취소된 주문의 결제를 무효로 하거나 취소 요청을 남긴다(PAY-3, docs/architecture/payment.md "보상 취소"). 결제 행을 잠그고 바꾼다.
 * 토스는 부르지 않는다. 승인된 결제의 토스 전액 취소는 {@link CancelApprovedPaymentUseCase}가 한다. 결제가 없는 주문(예약 중에 취소)은
 * 넘긴다.
 */
@Service
public class RequestPaymentCancelUseCase {

    private static final Logger log = LoggerFactory.getLogger(RequestPaymentCancelUseCase.class);

    private final PaymentRepository payments;
    private final Clock clock;

    public RequestPaymentCancelUseCase(PaymentRepository payments, Clock clock) {
        this.payments = payments;
        this.clock = clock;
    }

    @Transactional
    public void execute(RequestPaymentCancelCommand command) {
        payments.findForUpdateByOrderId(command.orderId())
                .ifPresent(
                        payment -> {
                            Payment.CancelRequest request = payment.requestCancel(clock.instant());
                            if (request == Payment.CancelRequest.ALREADY) {
                                return;
                            }
                            payments.save(payment);
                            if (request == Payment.CancelRequest.REQUESTED) {
                                log.info(
                                        "취소된 주문의 결제에 취소 요청을 남겼다: paymentId={}, status={}",
                                        payment.id(),
                                        payment.status());
                            }
                        });
    }
}
