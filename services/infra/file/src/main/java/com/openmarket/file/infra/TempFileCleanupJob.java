package com.openmarket.file.infra;

import com.openmarket.file.command.application.DeleteExpiredTempFileCommand;
import com.openmarket.file.command.application.DeleteExpiredTempFileUseCase;
import com.openmarket.file.command.domain.StoredFile;
import com.openmarket.file.command.domain.StoredFileRepository;
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
 * 24시간 지난 {@code TEMP} 파일을 지운다(CAT-1 AC). 인스턴스가 여러 개여도 ShedLock으로 한 곳에서만 돈다. 파일마다 트랜잭션을 따로 열고, 한 번에
 * 다 지우지 못하면 다음 주기에 이어 지운다.
 */
@Component
public class TempFileCleanupJob {

    static final String LOCK_NAME = "file-temp-cleanup";
    private static final int BATCH_SIZE = 100;
    private static final Logger log = LoggerFactory.getLogger(TempFileCleanupJob.class);

    private final StoredFileRepository files;
    private final DeleteExpiredTempFileUseCase deleteExpired;
    private final LockingTaskExecutor lockingTaskExecutor;
    private final Clock clock;

    public TempFileCleanupJob(
            StoredFileRepository files,
            DeleteExpiredTempFileUseCase deleteExpired,
            LockingTaskExecutor lockingTaskExecutor,
            Clock clock) {
        this.files = files;
        this.deleteExpired = deleteExpired;
        this.lockingTaskExecutor = lockingTaskExecutor;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${openmarket.file.cleanup-interval:10m}",
            initialDelayString = "${openmarket.file.cleanup-interval:10m}")
    public void run() {
        lockingTaskExecutor.executeWithLock(
                (Runnable) this::cleanup,
                new LockConfiguration(
                        clock.instant(), LOCK_NAME, Duration.ofMinutes(30), Duration.ZERO));
    }

    /** 잠금 없이 한 번 지운다. 지운(시도한) 파일 수를 돌려준다. */
    public int cleanup() {
        List<UUID> expired =
                files.findExpiredTempIds(clock.instant().minus(StoredFile.TEMP_TTL), BATCH_SIZE);
        expired.forEach(id -> deleteExpired.execute(new DeleteExpiredTempFileCommand(id)));
        if (!expired.isEmpty()) {
            log.info("만료된 TEMP 파일 {}개를 지웠다", expired.size());
        }
        return expired.size();
    }
}
