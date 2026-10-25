package com.openmarket.settlement.infra;

import com.openmarket.settlement.command.application.RunSettlementUseCase;
import java.time.Clock;
import java.time.Duration;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 정산 작업(docs/architecture/settlement.md "정산 확정"). 기본 매주 월요일 03:00(한국 시간)에 돈다. 인스턴스가 여러 개여도
 * ShedLock({@code settlement-confirm})으로 한 곳에서만 돈다. 관리자 즉시 실행은 잠금 없이 같은 유스케이스를 부르고, 겹쳐도 결과가 같다.
 */
@Component
public class SettlementJob {

    static final String LOCK_NAME = "settlement-confirm";
    private static final Logger log = LoggerFactory.getLogger(SettlementJob.class);

    private final RunSettlementUseCase run;
    private final LockingTaskExecutor lockingTaskExecutor;
    private final Clock clock;

    public SettlementJob(
            RunSettlementUseCase run, LockingTaskExecutor lockingTaskExecutor, Clock clock) {
        this.run = run;
        this.lockingTaskExecutor = lockingTaskExecutor;
        this.clock = clock;
    }

    @Scheduled(cron = "${openmarket.settlement.job.cron:0 0 3 * * MON}", zone = "Asia/Seoul")
    public void schedule() {
        lockingTaskExecutor.executeWithLock(
                (Runnable) this::confirm,
                new LockConfiguration(
                        clock.instant(), LOCK_NAME, Duration.ofMinutes(30), Duration.ZERO));
    }

    /** 잠금 없이 한 번 돈다. 확정한 정산 수를 돌려준다. */
    public int confirm() {
        int confirmed = run.execute();
        log.info("정산 작업: 정산 {}건을 확정했다", confirmed);
        return confirmed;
    }
}
