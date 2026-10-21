package com.openmarket.fulfillment.infra;

import com.openmarket.fulfillment.command.application.AutoConfirmPurchaseUseCase;
import com.openmarket.fulfillment.command.domain.ShipmentRepository;
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
 * 자동 구매확정(FUL-3, docs/architecture/fulfillment.md "구매확정"). 확정 기한이 지난 배송 완료 배송을 확정한다. 인스턴스가 여러 개여도
 * ShedLock으로 한 곳에서만 돈다. 배송마다 따로 확정하고, 한 번에 다 하지 못하면 다음 주기에 이어 한다.
 */
@Component
public class PurchaseConfirmJob {

    static final String LOCK_NAME = "fulfillment-purchase-confirm";
    private static final int BATCH_SIZE = 100;
    private static final Logger log = LoggerFactory.getLogger(PurchaseConfirmJob.class);

    private final ShipmentRepository shipments;
    private final AutoConfirmPurchaseUseCase autoConfirm;
    private final LockingTaskExecutor lockingTaskExecutor;
    private final Clock clock;

    public PurchaseConfirmJob(
            ShipmentRepository shipments,
            AutoConfirmPurchaseUseCase autoConfirm,
            LockingTaskExecutor lockingTaskExecutor,
            Clock clock) {
        this.shipments = shipments;
        this.autoConfirm = autoConfirm;
        this.lockingTaskExecutor = lockingTaskExecutor;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${openmarket.fulfillment.purchase-confirm.interval:1h}",
            initialDelayString = "${openmarket.fulfillment.purchase-confirm.interval:1h}")
    public void run() {
        lockingTaskExecutor.executeWithLock(
                (Runnable) this::confirm,
                new LockConfiguration(
                        clock.instant(), LOCK_NAME, Duration.ofMinutes(10), Duration.ZERO));
    }

    /** 잠금 없이 한 번 확정한다. 확정을 시도한 배송 수를 돌려준다. */
    public int confirm() {
        List<UUID> due = shipments.findConfirmDueIds(clock.instant(), BATCH_SIZE);
        for (UUID id : due) {
            try {
                autoConfirm.execute(id);
            } catch (RuntimeException e) {
                // 한 배송의 실패가 나머지를 막지 않게 한다. 다음 주기에 다시 한다.
                log.warn("자동 구매확정에 실패했다: shipmentId={}", id, e);
            }
        }
        if (!due.isEmpty()) {
            log.info("확정 기한이 지난 배송 {}건을 자동 구매확정했다", due.size());
        }
        return due.size();
    }
}
