package com.openmarket.notification.command.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InAppNotificationTest {

    private static final Instant NOW = Instant.parse("2026-10-22T00:00:00Z");

    private static Notification inApp(NotificationAudience audience, UUID recipient) {
        return Notification.inApp(
                UUID.randomUUID(),
                UUID.randomUUID(),
                NotificationKind.ORDER_PAID,
                audience,
                recipient,
                "주문이 완료됐습니다.",
                NotificationTarget.ORDER,
                UUID.randomUUID(),
                NOW);
    }

    @Test
    @DisplayName("인앱 알림은 만들 때 바로 SENT이고 발송 작업이 보내지 않는다")
    void sentOnCreation() {
        Notification notification = inApp(NotificationAudience.BUYER, UUID.randomUUID());

        assertThat(notification.channel()).isEqualTo(NotificationChannel.IN_APP);
        assertThat(notification.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.isDue(NOW)).isFalse();
    }

    @Test
    @DisplayName("구매자·판매자 알림은 받는 사람의 같은 받는 쪽 알림함에만 있다")
    void personalInbox() {
        UUID memberId = UUID.randomUUID();
        Notification notification = inApp(NotificationAudience.SELLER, memberId);

        assertThat(notification.isIn(new Inbox(NotificationAudience.SELLER, memberId))).isTrue();
        assertThat(notification.isIn(new Inbox(NotificationAudience.BUYER, memberId))).isFalse();
        assertThat(notification.isIn(new Inbox(NotificationAudience.SELLER, UUID.randomUUID())))
                .isFalse();
    }

    @Test
    @DisplayName("관리자 알림은 받는 사람 없이 역할에 한 건이고, 관리자라면 누구의 알림함에도 있다")
    void adminInbox() {
        Notification notification = inApp(NotificationAudience.ADMIN, null);

        assertThat(notification.recipientAccountId()).isNull();
        assertThat(notification.isIn(new Inbox(NotificationAudience.ADMIN, UUID.randomUUID())))
                .isTrue();
        assertThat(notification.isIn(new Inbox(NotificationAudience.BUYER, UUID.randomUUID())))
                .isFalse();
    }

    @Test
    @DisplayName("관리자 알림에는 받는 사람이 없고, 구매자·판매자 알림에는 받는 사람이 있어야 한다")
    void recipientMatchesAudience() {
        assertThatThrownBy(() -> inApp(NotificationAudience.ADMIN, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> inApp(NotificationAudience.BUYER, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("이메일 알림은 어느 알림함에도 없다")
    void emailIsNotInInbox() {
        UUID memberId = UUID.randomUUID();
        Notification email =
                Notification.email(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        NotificationKind.EMAIL_VERIFICATION,
                        memberId,
                        "buyer@example.com",
                        Map.of(),
                        NOW);

        assertThat(email.isIn(new Inbox(NotificationAudience.BUYER, memberId))).isFalse();
    }
}
