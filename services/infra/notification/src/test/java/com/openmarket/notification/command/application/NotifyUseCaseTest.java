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

class NotifyUseCaseTest {

    final Fakes.Notifications notifications = new Fakes.Notifications();
    final NotifyUseCase useCase = new NotifyUseCase(notifications, Fakes.LINKS, Fakes.clock());

    final UUID buyerId = UUID.randomUUID();
    final UUID sellerA = UUID.randomUUID();
    final UUID sellerB = UUID.randomUUID();
    final UUID orderId = UUID.randomUUID();

    private NotifyCommand orderPaid(UUID eventId) {
        return new NotifyCommand(
                eventId,
                List.of(
                        new NotifyCommand.Notice(
                                NotifyCommand.Kind.ORDER_PAID,
                                NotifyCommand.Audience.BUYER,
                                buyerId,
                                Map.of("orderName", "반팔 티셔츠 외 1건", "amount", "59700"),
                                NotifyCommand.Target.ORDER,
                                orderId),
                        sellerNotice(sellerA),
                        sellerNotice(sellerB)));
    }

    private NotifyCommand.Notice sellerNotice(UUID sellerId) {
        return new NotifyCommand.Notice(
                NotifyCommand.Kind.SELLER_ORDER_RECEIVED,
                NotifyCommand.Audience.SELLER,
                sellerId,
                Map.of("productName", "반팔 티셔츠", "lineCount", "1", "amount", "19900"),
                NotifyCommand.Target.SELLER_ORDER,
                UUID.randomUUID());
    }

    @Test
    @DisplayName("이벤트 하나로 받는 사람마다 인앱 알림을 만들고, 문구를 정해 SENT로 저장한다")
    void createsNoticePerRecipient() {
        useCase.execute(orderPaid(UUID.randomUUID()));

        assertThat(
                        notifications.rows.values().stream()
                                .filter(n -> n.channel() == NotificationChannel.IN_APP))
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
    @DisplayName("이메일 종류는 같은 문구와 앱 링크로 회원 이메일 알림(PENDING, 주소 없음)도 만든다. 판매자 새 주문은 인앱만이다")
    void createsMemberEmail() {
        useCase.execute(orderPaid(UUID.randomUUID()));

        List<Notification> emails =
                notifications.rows.values().stream()
                        .filter(n -> n.channel() == NotificationChannel.EMAIL)
                        .toList();
        assertThat(emails)
                .singleElement()
                .satisfies(
                        email -> {
                            assertThat(email.kind()).isEqualTo(NotificationKind.ORDER_PAID);
                            assertThat(email.status()).isEqualTo(NotificationStatus.PENDING);
                            assertThat(email.recipientAccountId()).isEqualTo(buyerId);
                            assertThat(email.recipientEmail()).isNull();
                            assertThat(email.params())
                                    .containsEntry("message", "주문이 완료됐습니다. 반팔 티셔츠 외 1건, 59,700원")
                                    .containsEntry("link", "http://BUYER/ORDER/" + orderId);
                        });
    }

    @Test
    @DisplayName("같은 원인 이벤트를 다시 받으면 아무것도 만들지 않는다(NTF-2)")
    void idempotent() {
        UUID eventId = UUID.randomUUID();

        useCase.execute(orderPaid(eventId));
        useCase.execute(orderPaid(eventId));

        assertThat(notifications.rows).hasSize(4);
    }

    @Test
    @DisplayName("관리자 알림은 받는 사람 없이 한 건이다")
    void adminNotice() {
        useCase.execute(
                new NotifyCommand(
                        UUID.randomUUID(),
                        List.of(
                                new NotifyCommand.Notice(
                                        NotifyCommand.Kind.REFUND_FAILED,
                                        NotifyCommand.Audience.ADMIN,
                                        null,
                                        Map.of("code", "NOT_CANCELABLE_AMOUNT"),
                                        NotifyCommand.Target.REFUND,
                                        UUID.randomUUID()))));

        Notification notification = notifications.rows.values().iterator().next();
        assertThat(notification.recipientAccountId()).isNull();
        assertThat(notification.isIn(new Inbox(NotificationAudience.ADMIN, UUID.randomUUID())))
                .isTrue();
    }
}
