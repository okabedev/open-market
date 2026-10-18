package com.openmarket.notification.infra;

import com.openmarket.notification.command.application.SendEmailCommand;
import com.openmarket.notification.command.application.SendEmailUseCase;
import com.openmarket.notification.command.domain.NotificationRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 보낼 차례인 이메일 알림을 보낸다. 2초마다 돌고, 인스턴스가 여러 개여도 ShedLock으로 한 곳에서만 돈다. 알림마다 트랜잭션을 따로 연다. */
@Component
public class EmailDispatchJob {

    static final String LOCK_NAME = "notification-email-dispatch";
    private static final int BATCH_SIZE = 50;

    private final NotificationRepository notifications;
    private final SendEmailUseCase sendEmail;
    private final LockingTaskExecutor lockingTaskExecutor;
    private final Clock clock;

    public EmailDispatchJob(
            NotificationRepository notifications,
            SendEmailUseCase sendEmail,
            LockingTaskExecutor lockingTaskExecutor,
            Clock clock) {
        this.notifications = notifications;
        this.sendEmail = sendEmail;
        this.lockingTaskExecutor = lockingTaskExecutor;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${openmarket.notification.dispatch-interval:2s}")
    public void run() {
        lockingTaskExecutor.executeWithLock(
                (Runnable) this::dispatch,
                new LockConfiguration(
                        clock.instant(), LOCK_NAME, Duration.ofMinutes(5), Duration.ZERO));
    }

    /** 잠금 없이 한 번 보낸다. 보낸(시도한) 알림 수를 돌려준다. */
    public int dispatch() {
        List<UUID> due = notifications.findDueEmailIds(clock.instant(), BATCH_SIZE);
        due.forEach(id -> sendEmail.execute(new SendEmailCommand(id)));
        return due.size();
    }
}
