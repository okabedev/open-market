package com.openmarket.notification.command.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.notification.command.domain.Notification;
import com.openmarket.notification.command.domain.NotificationAudience;
import com.openmarket.notification.command.domain.NotificationKind;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReadNotificationUseCaseTest {

    final Fakes.Notifications notifications = new Fakes.Notifications();
    final ReadNotificationUseCase useCase =
            new ReadNotificationUseCase(notifications, Fakes.clock());

    private Notification saved(NotificationAudience audience, UUID recipient) {
        return notifications.save(
                Notification.inApp(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        NotificationKind.CLAIM_MEDIATION_REQUESTED,
                        audience,
                        recipient,
                        "문구",
                        null,
                        null,
                        Fakes.NOW));
    }

    @Test
    @DisplayName("내 알림을 읽음으로 남긴다. 다시 해도 읽음 하나다")
    void marksRead() {
        UUID buyerId = UUID.randomUUID();
        Notification notification = saved(NotificationAudience.BUYER, buyerId);

        useCase.execute(
                new ReadNotificationCommand(
                        notification.id(), ReadNotificationCommand.Audience.BUYER, buyerId));
        useCase.execute(
                new ReadNotificationCommand(
                        notification.id(), ReadNotificationCommand.Audience.BUYER, buyerId));

        assertThat(notifications.reads).containsExactly(notification.id() + ":" + buyerId);
    }

    @Test
    @DisplayName("다른 회원의 알림이나 다른 받는 쪽의 알림은 404 NOTIFICATION_NOT_FOUND")
    void othersNotFound() {
        UUID memberId = UUID.randomUUID();
        Notification notification = saved(NotificationAudience.SELLER, memberId);

        for (ReadNotificationCommand command :
                new ReadNotificationCommand[] {
                    new ReadNotificationCommand(
                            notification.id(),
                            ReadNotificationCommand.Audience.SELLER,
                            UUID.randomUUID()),
                    new ReadNotificationCommand(
                            notification.id(), ReadNotificationCommand.Audience.BUYER, memberId),
                    new ReadNotificationCommand(
                            UUID.randomUUID(), ReadNotificationCommand.Audience.SELLER, memberId)
                }) {
            assertThatThrownBy(() -> useCase.execute(command))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", "NOTIFICATION_NOT_FOUND");
        }
        assertThat(notifications.reads).isEmpty();
    }

    @Test
    @DisplayName("관리자 알림은 관리자마다 따로 읽는다")
    void adminsReadSeparately() {
        Notification notification = saved(NotificationAudience.ADMIN, null);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        useCase.execute(
                new ReadNotificationCommand(
                        notification.id(), ReadNotificationCommand.Audience.ADMIN, first));
        useCase.execute(
                new ReadNotificationCommand(
                        notification.id(), ReadNotificationCommand.Audience.ADMIN, second));

        assertThat(notifications.reads)
                .containsExactly(notification.id() + ":" + first, notification.id() + ":" + second);
    }
}
