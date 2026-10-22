package com.openmarket.notification.command.application;

import com.openmarket.notification.command.domain.Inbox;
import com.openmarket.notification.command.domain.NotificationAudience;
import com.openmarket.notification.command.domain.NotificationRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 알림함에 지금까지 온 인앱 알림을 모두 읽음으로 남긴다. 이미 읽은 알림의 읽은 시각은 그대로 둔다. */
@Service
public class ReadAllNotificationsUseCase {

    private final NotificationRepository notifications;
    private final Clock clock;

    public ReadAllNotificationsUseCase(NotificationRepository notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional
    public void execute(ReadAllNotificationsCommand command) {
        notifications.markAllRead(
                new Inbox(
                        NotificationAudience.valueOf(command.audience().name()),
                        command.accountId()),
                clock.instant());
    }
}
