package com.openmarket.notification.infra;

import com.openmarket.notification.command.application.PurgeNotificationsCommand;
import com.openmarket.notification.command.application.PurgeNotificationsUseCase;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 보관 기간({@code openmarket.notification.retention})이 지난 알림을 지운다(docs/architecture/notification.md
 * "보관"). 1시간마다 돌고 ShedLock으로 한 곳에서만 돈다. 1,000건씩 트랜잭션을 나눠 지운다.
 */
@Component
public class NotificationPurgeJob {

    static final String LOCK_NAME = "notification-purge";
    static final int BATCH_SIZE = 1_000;

    private final PurgeNotificationsUseCase purge;
    private final LockingTaskExecutor lockingTaskExecutor;
    private final Clock clock;
    private final Duration retention;

    public NotificationPurgeJob(
            PurgeNotificationsUseCase purge,
            LockingTaskExecutor lockingTaskExecutor,
            Clock clock,
            @Value("${openmarket.notification.retention:90d}") Duration retention) {
        this.purge = purge;
        this.lockingTaskExecutor = lockingTaskExecutor;
        this.clock = clock;
        this.retention = retention;
    }

    @Scheduled(
            fixedDelayString = "${openmarket.notification.purge-interval:1h}",
            initialDelayString = "${openmarket.notification.purge-interval:1h}")
    public void run() {
        lockingTaskExecutor.executeWithLock(
                (Runnable) this::purge,
                new LockConfiguration(
                        clock.instant(), LOCK_NAME, Duration.ofMinutes(30), Duration.ZERO));
    }

    /** 잠금 없이 한 번 정리한다. 지운 수를 돌려준다. */
    public int purge() {
        Instant before = clock.instant().minus(retention);
        int total = 0;
        int deleted;
        do {
            deleted = purge.execute(new PurgeNotificationsCommand(before, BATCH_SIZE));
            total += deleted;
        } while (deleted == BATCH_SIZE);
        return total;
    }
}
