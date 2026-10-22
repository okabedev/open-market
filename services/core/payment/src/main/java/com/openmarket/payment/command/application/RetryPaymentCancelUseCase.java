package com.openmarket.payment.command.application;

import com.openmarket.common.core.audit.AuditEntry;
import com.openmarket.common.core.audit.AuditLog;
import com.openmarket.payment.command.domain.Payment;
import com.openmarket.payment.command.domain.PaymentNotFoundException;
import com.openmarket.payment.command.domain.PaymentRepository;
import java.time.Clock;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 보상 취소 재시도(docs/architecture/payment.md "관리자 재시도"). 결제를 잠그고 취소 실패 코드를 지워, 취소 작업이 다음 주기에 새
 * {@code Idempotency-Key}로 토스를 부르게 한다. 감사 로그를 남긴다.
 */
@Service
public class RetryPaymentCancelUseCase {

    private final PaymentRepository payments;
    private final AuditLog auditLog;
    private final Clock clock;

    public RetryPaymentCancelUseCase(PaymentRepository payments, AuditLog auditLog, Clock clock) {
        this.payments = payments;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Transactional
    public void execute(RetryPaymentCancelCommand command) {
        Payment payment =
                payments.findForUpdateById(command.paymentId())
                        .orElseThrow(PaymentNotFoundException::new);
        String previousCode = payment.cancelFailureCode();
        if (payment.retryCancel(clock.instant()) == Payment.Transition.ALREADY) {
            return;
        }
        payments.save(payment);
        auditLog.record(
                new AuditEntry(
                        command.adminId().toString(),
                        "PAYMENT_CANCEL_RETRIED",
                        "PAYMENT",
                        payment.id().toString(),
                        Objects.requireNonNullElse(previousCode, "")));
    }
}
