package com.openmarket.notification.command.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationTest {

    private static final Instant NOW = Instant.parse("2026-10-18T00:00:00Z");

    private static Notification pending() {
        return Notification.email(
                UUID.randomUUID(),
                UUID.randomUUID(),
                NotificationKind.EMAIL_VERIFICATION,
                UUID.randomUUID(),
                "buyer@example.com",
                Map.of("verificationUrl", "http://localhost:3000/verify-email?token=secret"),
                NOW);
    }

    @Test
    @DisplayName("이메일 알림은 PENDING으로 시작하고 바로 보낼 수 있다")
    void startsPending() {
        Notification notification = pending();

        assertThat(notification.status()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.channel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(notification.isDue(NOW)).isTrue();
    }

    @Test
    @DisplayName("보내면 SENT가 되고 본문 값(인증 링크 등)을 지운다")
    void sentClearsParams() {
        Notification notification = pending();

        notification.markSent(NOW);

        assertThat(notification.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.params()).isEmpty();
        assertThat(notification.isDue(NOW.plusSeconds(3600))).isFalse();
    }

    @Test
    @DisplayName("보내지 못하면 30초, 1분, 2분, 4분 뒤에 다시 시도하고 5번째 실패에서 FAILED가 된다")
    void retriesWithBackoffThenFails() {
        Notification notification = pending();

        notification.markFailed(NOW);
        assertThat(notification.isDue(NOW.plusSeconds(29))).isFalse();
        assertThat(notification.isDue(NOW.plusSeconds(30))).isTrue();

        notification.markFailed(NOW);
        assertThat(notification.isDue(NOW.plus(Duration.ofMinutes(1)))).isTrue();
        notification.markFailed(NOW);
        notification.markFailed(NOW);
        assertThat(notification.status()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.isDue(NOW.plus(Duration.ofMinutes(4)))).isTrue();

        notification.markFailed(NOW);

        assertThat(notification.status()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.isDue(NOW.plus(Duration.ofDays(1)))).isFalse();
        assertThat(notification.params()).isEmpty();
    }
}
