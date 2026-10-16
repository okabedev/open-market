package com.openmarket.common.web.idempotency;

import java.time.Clock;
import java.time.Duration;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 보관 기간(24시간)이 지난 멱등성 키 기록을 한 시간에 한 번 지운다. 지난 기록은 어차피 새 요청으로 처리되므로 테이블 크기만 관리한다.
 *
 * <p>락 실행기와 스케줄링은 {@code common-messaging}이 등록한다. 그 라이브러리가 없으면 이 작업도 등록되지 않는다.
 */
public class IdempotencyCleanupJob {

    static final String LOCK_NAME = "common-web-idempotency-cleanup";

    private static final Logger log = LoggerFactory.getLogger(IdempotencyCleanupJob.class);

    private final IdempotencyStore store;
    private final LockingTaskExecutor lockingTaskExecutor;
    private final Clock clock;

    public IdempotencyCleanupJob(
            IdempotencyStore store, LockingTaskExecutor lockingTaskExecutor, Clock clock) {
        this.store = store;
        this.lockingTaskExecutor = lockingTaskExecutor;
        this.clock = clock;
    }

    @Scheduled(cron = "${openmarket.web.idempotency-cleanup-cron:0 15 * * * *}")
    public void run() {
        lockingTaskExecutor.executeWithLock(
                (Runnable) this::cleanUp,
                new LockConfiguration(
                        clock.instant(), LOCK_NAME, Duration.ofMinutes(10), Duration.ofMinutes(1)));
    }

    /** 잠금 없이 바로 지운다. 지운 행 수를 돌려준다. */
    public int cleanUp() {
        int deleted = store.deleteOlderThan(JdbcIdempotencyStore.RETENTION);
        log.info("멱등성 키 기록 정리: {}행", deleted);
        return deleted;
    }
}
