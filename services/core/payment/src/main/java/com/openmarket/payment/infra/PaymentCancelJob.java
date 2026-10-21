package com.openmarket.payment.infra;

import com.openmarket.payment.command.application.CancelApprovedPaymentUseCase;
import com.openmarket.payment.command.domain.PaymentRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 취소된 주문의 승인된 결제를 토스에서 전액 취소한다(PAY-3, docs/architecture/payment.md "보상 취소"). 인스턴스가 여러 개여도
 * ShedLock으로 한 곳에서만 돈다. 결제마다 따로 취소하고, 한 번에 다 하지 못하면 다음 주기에 이어 한다.
 */
@Component
public class PaymentCancelJob {

    static final String LOCK_NAME = "payment-cancel";
    private static final int BATCH_SIZE = 100;
    private static final Logger log = LoggerFactory.getLogger(PaymentCancelJob.class);

    private final PaymentRepository payments;
    private final CancelApprovedPaymentUseCase cancelApproved;
    private final LockingTaskExecutor lockingTaskExecutor;
    private final Clock clock;

    public PaymentCancelJob(
            PaymentRepository payments,
            CancelApprovedPaymentUseCase cancelApproved,
            LockingTaskExecutor lockingTaskExecutor,
            Clock clock) {
        this.payments = payments;
        this.cancelApproved = cancelApproved;
        this.lockingTaskExecutor = lockingTaskExecutor;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${openmarket.payment.cancel-interval:1m}",
            initialDelayString = "${openmarket.payment.cancel-interval:1m}")
    public void run() {
        lockingTaskExecutor.executeWithLock(
                (Runnable) this::cancel,
                new LockConfiguration(
                        clock.instant(), LOCK_NAME, Duration.ofMinutes(10), Duration.ZERO));
    }

    /** 잠금 없이 한 번 취소한다. 취소를 시도한 결제 수를 돌려준다. */
    public int cancel() {
        List<UUID> targets = payments.findCancelRequestedApprovedIds(BATCH_SIZE);
        for (UUID id : targets) {
            try {
                cancelApproved.execute(id);
            } catch (RuntimeException e) {
                // 한 결제의 실패가 나머지를 막지 않게 한다. 다음 주기에 다시 취소한다.
                log.warn("결제 보상 취소에 실패했다: paymentId={}", id, e);
            }
        }
        if (!targets.isEmpty()) {
            log.info("취소된 주문의 승인 결제 {}건의 토스 취소를 시도했다", targets.size());
        }
        return targets.size();
    }
}
