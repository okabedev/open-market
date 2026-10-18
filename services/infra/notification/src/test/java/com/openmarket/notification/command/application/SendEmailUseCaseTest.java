package com.openmarket.notification.command.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmarket.notification.command.domain.Notification;
import com.openmarket.notification.command.domain.NotificationKind;
import com.openmarket.notification.command.domain.NotificationStatus;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SendEmailUseCaseTest {

    private final Fakes.Notifications notifications = new Fakes.Notifications();
    private final Fakes.Mailbox mailbox = new Fakes.Mailbox();
    private final SendEmailUseCase useCase =
            new SendEmailUseCase(notifications, Fakes.RENDERER, mailbox, Fakes.clock());

    private Notification pending() {
        return notifications.save(
                Notification.email(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        NotificationKind.EMAIL_VERIFICATION,
                        UUID.randomUUID(),
                        "buyer@example.com",
                        Map.of("verificationUrl", "http://x/?token=t"),
                        Fakes.NOW));
    }

    @Test
    @DisplayName("본문을 만들어 보내고 SENT로 바꾼다")
    void sends() {
        Notification notification = pending();

        useCase.execute(new SendEmailCommand(notification.id()));

        assertThat(mailbox.sent)
                .singleElement()
                .satisfies(
                        mail -> {
                            assertThat(mail.to()).isEqualTo("buyer@example.com");
                            assertThat(mail.subject()).isEqualTo("제목 EMAIL_VERIFICATION");
                            assertThat(mail.text()).contains("http://x/?token=t");
                        });
        assertThat(notification.status()).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    @DisplayName("이미 보낸 알림은 다시 보내지 않는다")
    void skipsSent() {
        Notification notification = pending();
        useCase.execute(new SendEmailCommand(notification.id()));

        useCase.execute(new SendEmailCommand(notification.id()));

        assertThat(mailbox.sent).hasSize(1);
    }

    @Test
    @DisplayName("SMTP가 실패하면 예외 없이 다음 시도로 미룬다")
    void defersOnFailure() {
        Notification notification = pending();
        mailbox.failing = true;

        useCase.execute(new SendEmailCommand(notification.id()));

        assertThat(notification.status()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.isDue(Fakes.NOW)).isFalse();
    }
}
