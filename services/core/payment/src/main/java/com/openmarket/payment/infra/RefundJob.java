package com.openmarket.payment.infra;

import com.openmarket.payment.command.application.ProcessRefundUseCase;
import com.openmarket.payment.command.domain.RefundRepository;
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
 * 클레임 환불의 토스 부분취소를 부른다(PAY-2, docs/architecture/payment.md "환불 작업"). 인스턴스가 여러 개여도 ShedLock으로 한 곳에서만
 * 돈다. 환불마다 따로 부르고, 한 번에 다 하지 못하면 다음 주기에 이어 한다.
 */
@Component
public class RefundJob {

    static final String LOCK_NAME = "payment-refund";
    private static final int BATCH_SIZE = 100;
    private static final Logger log = LoggerFactory.getLogger(RefundJob.class);

    private final RefundRepository refunds;
    private final ProcessRefundUseCase process;
    private final LockingTaskExecutor lockingTaskExecutor;
    private final Clock clock;

    public RefundJob(
            RefundRepository refunds,
            ProcessRefundUseCase process,
            LockingTaskExecutor lockingTaskExecutor,
            Clock clock) {
        this.refunds = refunds;
        this.process = process;
        this.lockingTaskExecutor = lockingTaskExecutor;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${openmarket.payment.refund-interval:1m}",
            initialDelayString = "${openmarket.payment.refund-interval:1m}")
    public void run() {
        lockingTaskExecutor.executeWithLock(
                (Runnable) this::refund,
                new LockConfiguration(
                        clock.instant(), LOCK_NAME, Duration.ofMinutes(10), Duration.ZERO));
    }

    /** 잠금 없이 한 번 환불한다. 토스를 부른 환불 수를 돌려준다. */
    public int refund() {
        List<UUID> requested = refunds.findRequestedIds(BATCH_SIZE);
        for (UUID id : requested) {
            try {
                process.execute(id);
            } catch (RuntimeException e) {
                // 한 환불의 실패가 나머지를 막지 않게 한다. 다음 주기에 다시 부른다.
                log.warn("환불에 실패했다: refundId={}", id, e);
            }
        }
        if (!requested.isEmpty()) {
            log.info("환불 {}건의 토스 부분취소를 시도했다", requested.size());
        }
        return requested.size();
    }
}
