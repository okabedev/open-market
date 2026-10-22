package com.openmarket.payment.command.application;

import com.openmarket.common.core.audit.AuditEntry;
import com.openmarket.common.core.audit.AuditLog;
import com.openmarket.payment.command.domain.Payment;
import com.openmarket.payment.command.domain.PaymentRepository;
import com.openmarket.payment.command.domain.Refund;
import com.openmarket.payment.command.domain.RefundNotFoundException;
import com.openmarket.payment.command.domain.RefundRepository;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 환불 재시도(docs/architecture/payment.md "관리자 재시도"). 환불 작업과 같은 순서(결제 → 환불)로 잠그고, 환불 요청의 확인을 다시 한 뒤
 * {@code REQUESTED}로 돌린다. 환불 작업이 새 {@code Idempotency-Key}로 토스를 부른다. 감사 로그를 남긴다.
 */
@Service
public class RetryRefundUseCase {

    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final AuditLog auditLog;
    private final Clock clock;

    public RetryRefundUseCase(
            PaymentRepository payments, RefundRepository refunds, AuditLog auditLog, Clock clock) {
        this.payments = payments;
        this.refunds = refunds;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Transactional
    public void execute(RetryRefundCommand command) {
        Refund found =
                refunds.findById(command.refundId()).orElseThrow(RefundNotFoundException::new);
        Optional<Payment> payment = payments.findForUpdateByOrderId(found.orderId());
        Refund refund =
                refunds.findForUpdateById(command.refundId())
                        .orElseThrow(RefundNotFoundException::new);
        String previousCode = refund.failureCode();
        long available =
                payment.map(
                                p ->
                                        p.remainingAmount()
                                                - refunds.sumRequestedAmountByPaymentId(p.id()))
                        .orElse(0L);
        if (refund.retry(payment.orElse(null), available, clock.instant())
                == Refund.Transition.ALREADY) {
            return;
        }
        refunds.save(refund);
        auditLog.record(
                new AuditEntry(
                        command.adminId().toString(),
                        "REFUND_RETRIED",
                        "REFUND",
                        refund.id().toString(),
                        Objects.requireNonNullElse(previousCode, "")));
    }
}
