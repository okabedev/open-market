package com.openmarket.payment.command.application;

import com.openmarket.payment.command.domain.Payment;
import com.openmarket.payment.command.domain.PaymentRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제를 기다리는 주문의 {@code READY} 결제를 만든다(docs/architecture/payment.md "결제 준비"). 같은 주문의 결제가 이미 있으면 아무것도
 * 하지 않는다.
 */
@Service
public class PreparePaymentUseCase {

    private final PaymentRepository payments;
    private final Clock clock;

    public PreparePaymentUseCase(PaymentRepository payments, Clock clock) {
        this.payments = payments;
        this.clock = clock;
    }

    @Transactional
    public void execute(PreparePaymentCommand command) {
        if (payments.existsByOrderId(command.orderId())) {
            return;
        }
        payments.save(
                Payment.ready(
                        command.orderId(),
                        command.buyerId(),
                        command.amount(),
                        command.orderName(),
                        clock.instant()));
    }
}
