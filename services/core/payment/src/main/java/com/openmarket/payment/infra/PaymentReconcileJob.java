package com.openmarket.payment.infra;

import com.openmarket.payment.command.application.ReconcilePaymentUseCase;
import com.openmarket.payment.command.domain.PaymentRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 승인 결과를 확인하지 못한 {@code IN_PROGRESS} 결제를 토스 조회로 정한다(docs/architecture/payment.md "재조회 작업"). 인스턴스가
 * 여러 개여도 ShedLock으로 한 곳에서만 돈다. 결제마다 따로 조회하고, 한 번에 다 하지 못하면 다음 주기에 이어 한다.
 */
@Component
public class PaymentReconcileJob {

    static final String LOCK_NAME = "payment-reconcile";
    private static final int BATCH_SIZE = 100;
    private static final Logger log = LoggerFactory.getLogger(PaymentReconcileJob.class);

    private final PaymentRepository payments;
    private final ReconcilePaymentUseCase reconcile;
    private final LockingTaskExecutor lockingTaskExecutor;
    private final Clock clock;
    private final Duration reconcileAfter;

    public PaymentReconcileJob(
            PaymentRepository payments,
            ReconcilePaymentUseCase reconcile,
            LockingTaskExecutor lockingTaskExecutor,
            Clock clock,
            @Value("${openmarket.payment.reconcile-after:1m}") Duration reconcileAfter) {
        this.payments = payments;
        this.reconcile = reconcile;
        this.lockingTaskExecutor = lockingTaskExecutor;
        this.clock = clock;
        this.reconcileAfter = reconcileAfter;
    }

    @Scheduled(
            fixedDelayString = "${openmarket.payment.reconcile-interval:1m}",
            initialDelayString = "${openmarket.payment.reconcile-interval:1m}")
    public void run() {
        lockingTaskExecutor.executeWithLock(
                (Runnable) this::reconcile,
                new LockConfiguration(
                        clock.instant(), LOCK_NAME, Duration.ofMinutes(10), Duration.ZERO));
    }

    /** 잠금 없이 한 번 재조회한다. 조회한 결제 수를 돌려준다. */
    public int reconcile() {
        List<UUID> pending =
                payments.findInProgressIdsRequestedBefore(
                        clock.instant().minus(reconcileAfter), BATCH_SIZE);
        for (UUID id : pending) {
            try {
                reconcile.execute(id);
            } catch (RuntimeException e) {
                // 한 결제의 실패가 나머지를 막지 않게 한다. 다음 주기에 다시 조회한다.
                log.warn("결제 재조회에 실패했다: paymentId={}", id, e);
            }
        }
        if (!pending.isEmpty()) {
            log.info("승인 결과를 기다리는 결제 {}건을 재조회했다", pending.size());
        }
        return pending.size();
    }
}
