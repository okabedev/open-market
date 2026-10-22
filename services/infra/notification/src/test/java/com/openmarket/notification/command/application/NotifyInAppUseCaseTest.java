package com.openmarket.notification.command.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmarket.notification.command.domain.Inbox;
import com.openmarket.notification.command.domain.Notification;
import com.openmarket.notification.command.domain.NotificationAudience;
import com.openmarket.notification.command.domain.NotificationChannel;
import com.openmarket.notification.command.domain.NotificationKind;
import com.openmarket.notification.command.domain.NotificationStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotifyInAppUseCaseTest {

    final Fakes.Notifications notifications = new Fakes.Notifications();
    final NotifyInAppUseCase useCase = new NotifyInAppUseCase(notifications, Fakes.clock());

    final UUID buyerId = UUID.randomUUID();
    final UUID sellerA = UUID.randomUUID();
    final UUID sellerB = UUID.randomUUID();
    final UUID orderId = UUID.randomUUID();

    private NotifyInAppCommand orderPaid(UUID eventId) {
        return new NotifyInAppCommand(
                eventId,
                List.of(
                        new NotifyInAppCommand.Notice(
                                NotifyInAppCommand.Kind.ORDER_PAID,
                                NotifyInAppCommand.Audience.BUYER,
                                buyerId,
                                Map.of("orderName", "반팔 티셔츠 외 1건", "amount", "59700"),
                                NotifyInAppCommand.Target.ORDER,
                                orderId),
                        sellerNotice(sellerA),
                        sellerNotice(sellerB)));
    }

    private NotifyInAppCommand.Notice sellerNotice(UUID sellerId) {
        return new NotifyInAppCommand.Notice(
                NotifyInAppCommand.Kind.SELLER_ORDER_RECEIVED,
                NotifyInAppCommand.Audience.SELLER,
                sellerId,
                Map.of("productName", "반팔 티셔츠", "lineCount", "1", "amount", "19900"),
                NotifyInAppCommand.Target.SELLER_ORDER,
                UUID.randomUUID());
    }

    @Test
    @DisplayName("이벤트 하나로 받는 사람마다 인앱 알림을 만들고, 문구를 정해 SENT로 저장한다")
    void createsNoticePerRecipient() {
        useCase.execute(orderPaid(UUID.randomUUID()));

        assertThat(notifications.rows.values())
                .extracting(Notification::kind)
                .containsExactly(
                        NotificationKind.ORDER_PAID,
                        NotificationKind.SELLER_ORDER_RECEIVED,
                        NotificationKind.SELLER_ORDER_RECEIVED);
        Notification buyer = notifications.rows.values().iterator().next();
        assertThat(buyer.channel()).isEqualTo(NotificationChannel.IN_APP);
        assertThat(buyer.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(buyer.message()).isEqualTo("주문이 완료됐습니다. 반팔 티셔츠 외 1건, 59,700원");
        assertThat(buyer.isIn(new Inbox(NotificationAudience.BUYER, buyerId))).isTrue();
        assertThat(
                        notifications.rows.values().stream()
                                .filter(
                                        n ->
                                                n.isIn(
                                                        new Inbox(
                                                                NotificationAudience.SELLER,
                                                                sellerB)))
                                .count())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("같은 원인 이벤트를 다시 받으면 아무것도 만들지 않는다(NTF-2)")
    void idempotent() {
        UUID eventId = UUID.randomUUID();

        useCase.execute(orderPaid(eventId));
        useCase.execute(orderPaid(eventId));

        assertThat(notifications.rows).hasSize(3);
    }

    @Test
    @DisplayName("관리자 알림은 받는 사람 없이 한 건이다")
    void adminNotice() {
        useCase.execute(
                new NotifyInAppCommand(
                        UUID.randomUUID(),
                        List.of(
                                new NotifyInAppCommand.Notice(
                                        NotifyInAppCommand.Kind.REFUND_FAILED,
                                        NotifyInAppCommand.Audience.ADMIN,
                                        null,
                                        Map.of("code", "NOT_CANCELABLE_AMOUNT"),
                                        NotifyInAppCommand.Target.REFUND,
                                        UUID.randomUUID()))));

        Notification notification = notifications.rows.values().iterator().next();
        assertThat(notification.recipientAccountId()).isNull();
        assertThat(notification.isIn(new Inbox(NotificationAudience.ADMIN, UUID.randomUUID())))
                .isTrue();
    }
}
