package com.openmarket.common.messaging.cleanup;

import java.time.Clock;
import java.time.Duration;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 보관 기간이 지난 {@code outbox_event}(7일), {@code processed_event}(30일), 처리가 끝난 {@code dead_letter}(다시
 * 보내거나 버린 지 30일) 행을 하루에 한 번 지운다. 처리하지 않은 DLT 메시지({@code PENDING})는 지우지 않는다. ShedLock으로 인스턴스가 여러 개여도
 * 한 번만 실행된다. 다시 실행해도 결과가 같다.
 *
 * <p>잠금을 오래 잡지 않도록 일정 행 수씩 나눠 지운다.
 */
public class MessagingCleanupJob {

    static final Duration OUTBOX_RETENTION = Duration.ofDays(7);
    static final Duration PROCESSED_EVENT_RETENTION = Duration.ofDays(30);
    static final Duration DEAD_LETTER_RETENTION = Duration.ofDays(30);
    static final String LOCK_NAME = "common-messaging-cleanup";

    private static final Logger log = LoggerFactory.getLogger(MessagingCleanupJob.class);
    private static final int BATCH_SIZE = 5_000;

    private final JdbcClient jdbc;
    private final LockingTaskExecutor lockingTaskExecutor;
    private final Clock clock;

    public MessagingCleanupJob(
            JdbcClient jdbc, LockingTaskExecutor lockingTaskExecutor, Clock clock) {
        this.jdbc = jdbc;
        this.lockingTaskExecutor = lockingTaskExecutor;
        this.clock = clock;
    }

    @Scheduled(cron = "${openmarket.messaging.cleanup-cron:0 0 4 * * *}", zone = "Asia/Seoul")
    public void run() {
        lockingTaskExecutor.executeWithLock(
                (Runnable) this::cleanUp,
                new LockConfiguration(
                        clock.instant(), LOCK_NAME, Duration.ofMinutes(30), Duration.ofMinutes(1)));
    }

    /** 잠금 없이 바로 지운다. 지운 행 수를 돌려준다. */
    public Result cleanUp() {
        int outbox = deleteInBatches("outbox_event", "occurred_at", OUTBOX_RETENTION);
        int processed =
                deleteInBatches("processed_event", "processed_at", PROCESSED_EVENT_RETENTION);
        // resolved_at은 PENDING이면 비어 있어 지워지지 않는다.
        int deadLetters = deleteInBatches("dead_letter", "resolved_at", DEAD_LETTER_RETENTION);
        log.info(
                "메시징 테이블 정리: outbox_event {}행, processed_event {}행, dead_letter {}행",
                outbox,
                processed,
                deadLetters);
        return new Result(outbox, processed, deadLetters);
    }

    private int deleteInBatches(String table, String timeColumn, Duration retention) {
        // 테이블과 컬럼 이름은 이 클래스의 상수만 들어온다.
        String sql =
                "DELETE FROM "
                        + table
                        + " WHERE ctid IN (SELECT ctid FROM "
                        + table
                        + " WHERE "
                        + timeColumn
                        + " < now() - make_interval(secs => :seconds) LIMIT :batch)";
        int total = 0;
        int deleted;
        do {
            deleted =
                    jdbc.sql(sql)
                            .param("seconds", retention.toSeconds())
                            .param("batch", BATCH_SIZE)
                            .update();
            total += deleted;
        } while (deleted == BATCH_SIZE);
        return total;
    }

    public record Result(int outboxEvents, int processedEvents, int deadLetters) {}
}
