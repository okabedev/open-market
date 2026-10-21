package com.openmarket.payment.command.application;

import com.openmarket.payment.command.domain.Payment;
import com.openmarket.payment.command.domain.PaymentLedgerEntry;
import com.openmarket.payment.command.domain.PaymentRepository;
import com.openmarket.payment.command.domain.PaymentStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * 승인 결과를 확인하지 못한 {@code IN_PROGRESS} 결제를 토스 조회로 정한다(docs/architecture/payment.md "재조회 작업"). 조회는
 * 트랜잭션 밖에서 한다. 이미 정해진 결제는 넘긴다.
 */
@Service
public class ReconcilePaymentUseCase {

    private final PaymentRepository payments;
    private final PaymentGatewayPort gateway;
    private final ApplyGatewayResultUseCase apply;
    private final TransactionOperations transaction;

    public ReconcilePaymentUseCase(
            PaymentRepository payments,
            PaymentGatewayPort gateway,
            ApplyGatewayResultUseCase apply,
            TransactionOperations transaction) {
        this.payments = payments;
        this.gateway = gateway;
        this.apply = apply;
        this.transaction = transaction;
    }

    public void execute(UUID paymentId) {
        Optional<Target> target =
                transaction.execute(
                        _ ->
                                payments.findForUpdateById(paymentId)
                                        .filter(p -> p.status() == PaymentStatus.IN_PROGRESS)
                                        .map(ReconcilePaymentUseCase::target));
        if (target == null || target.isEmpty()) {
            return;
        }
        Target found = target.get();
        apply.execute(
                paymentId,
                found.attempt(),
                PaymentLedgerEntry.Kind.QUERY,
                found.paymentKey(),
                gateway.find(found.paymentKey()));
    }

    private static Target target(Payment payment) {
        String paymentKey = payment.paymentKey();
        if (paymentKey == null) {
            throw new IllegalStateException("결제 키가 없는 IN_PROGRESS 결제다: " + payment.id());
        }
        return new Target(paymentKey, payment.confirmAttempt());
    }

    private record Target(String paymentKey, int attempt) {}
}
