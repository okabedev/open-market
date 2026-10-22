package com.openmarket.notification.command.application;

import com.openmarket.notification.command.domain.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보관 기간이 지난 알림을 한 묶음 지운다(docs/architecture/notification.md "보관"). 보낼 차례를 기다리는 이메일은 남기고, 읽음 기록은 알림과
 * 함께 지워진다. 지운 수를 돌려준다.
 */
@Service
public class PurgeNotificationsUseCase {

    private final NotificationRepository notifications;

    public PurgeNotificationsUseCase(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    @Transactional
    public int execute(PurgeNotificationsCommand command) {
        return notifications.deleteCreatedBefore(command.before(), command.limit());
    }
}
