package com.openmarket.notification.command.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmarket.notification.command.domain.NotificationKind;
import com.openmarket.notification.command.domain.NotificationStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotifyEmailVerificationUseCaseTest {

    private final Fakes.Notifications notifications = new Fakes.Notifications();
    private final NotifyEmailVerificationUseCase useCase =
            new NotifyEmailVerificationUseCase(notifications, Fakes.clock());

    private NotifyEmailVerificationCommand command(UUID eventId) {
        return new NotifyEmailVerificationCommand(
                eventId,
                UUID.randomUUID(),
                "buyer@example.com",
                "http://localhost:3000/verify-email?token=abc",
                Instant.parse("2026-10-19T00:00:00Z"));
    }

    @Test
    @DisplayName("인증 메일 알림을 PENDING으로 저장한다")
    void createsPendingEmail() {
        useCase.execute(command(UUID.randomUUID()));

        assertThat(notifications.rows.values())
                .singleElement()
                .satisfies(
                        n -> {
                            assertThat(n.kind()).isEqualTo(NotificationKind.EMAIL_VERIFICATION);
                            assertThat(n.status()).isEqualTo(NotificationStatus.PENDING);
                            assertThat(n.recipientEmail()).isEqualTo("buyer@example.com");
                            assertThat(n.params())
                                    .containsEntry(
                                            "verificationUrl",
                                            "http://localhost:3000/verify-email?token=abc")
                                    .containsKey("expiresAt");
                        });
    }

    @Test
    @DisplayName("같은 원인 이벤트로는 알림을 하나만 만든다(NTF-2)")
    void deduplicatesBySourceEvent() {
        UUID eventId = UUID.randomUUID();

        useCase.execute(command(eventId));
        useCase.execute(command(eventId));

        assertThat(notifications.rows).hasSize(1);
    }
}
