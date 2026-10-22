package com.openmarket.notification.command.application;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;
import com.openmarket.notification.command.domain.Inbox;
import com.openmarket.notification.command.domain.NotificationAudience;
import com.openmarket.notification.command.domain.NotificationRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 알림함의 인앱 알림 하나를 읽음으로 남긴다. 다시 해도 결과가 같다. 내 알림함에 없으면 404 {@code NOTIFICATION_NOT_FOUND}. */
@Service
public class ReadNotificationUseCase {

    private final NotificationRepository notifications;
    private final Clock clock;

    public ReadNotificationUseCase(NotificationRepository notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional
    public void execute(ReadNotificationCommand command) {
        Inbox inbox =
                new Inbox(
                        NotificationAudience.valueOf(command.audience().name()),
                        command.accountId());
        boolean visible =
                notifications
                        .findById(command.notificationId())
                        .filter(notification -> notification.isIn(inbox))
                        .isPresent();
        if (!visible) {
            throw new BusinessException(
                    ErrorType.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "알림을 찾을 수 없습니다.");
        }
        notifications.markRead(command.notificationId(), command.accountId(), clock.instant());
    }
}
