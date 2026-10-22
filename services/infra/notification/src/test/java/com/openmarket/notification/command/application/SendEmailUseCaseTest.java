package com.openmarket.notification.command.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmarket.notification.command.domain.Notification;
import com.openmarket.notification.command.domain.NotificationAudience;
import com.openmarket.notification.command.domain.NotificationKind;
import com.openmarket.notification.command.domain.NotificationStatus;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SendEmailUseCaseTest {

    private final Fakes.Notifications notifications = new Fakes.Notifications();
    private final Fakes.Mailbox mailbox = new Fakes.Mailbox();
    private final Fakes.Contacts contacts = new Fakes.Contacts();
    private final SendEmailUseCase useCase =
            new SendEmailUseCase(notifications, Fakes.RENDERER, mailbox, contacts, Fakes.clock());

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

    private Notification memberEmail(UUID memberId) {
        return notifications.save(
                Notification.memberEmail(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        NotificationKind.ORDER_PAID,
                        NotificationAudience.BUYER,
                        memberId,
                        Map.of("message", "주문이 완료됐습니다.", "link", "http://shop/orders/1"),
                        Fakes.NOW));
    }

    @Test
    @DisplayName("회원 알림은 보낼 때 member에서 인증한 주소를 조회해 보낸다. 주소는 저장하지 않는다")
    void looksUpMemberEmail() {
        UUID memberId = UUID.randomUUID();
        contacts.verified.put(memberId, "member@example.com");
        Notification notification = memberEmail(memberId);

        useCase.execute(new SendEmailCommand(notification.id()));

        assertThat(mailbox.sent)
                .singleElement()
                .extracting(MailSenderPort.OutgoingEmail::to)
                .isEqualTo("member@example.com");
        assertThat(notification.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.recipientEmail()).isNull();
    }

    @Test
    @DisplayName("회원이 없거나 탈퇴했거나 이메일을 인증하지 않았으면 보내지 않고 SKIPPED로 끝낸다")
    void skipsWithoutVerifiedEmail() {
        Notification notification = memberEmail(UUID.randomUUID());

        useCase.execute(new SendEmailCommand(notification.id()));

        assertThat(mailbox.sent).isEmpty();
        assertThat(notification.status()).isEqualTo(NotificationStatus.SKIPPED);
        assertThat(notification.params()).isEmpty();
        assertThat(notification.isDue(Fakes.NOW.plusSeconds(3600))).isFalse();
    }

    @Test
    @DisplayName("member를 부를 수 없으면 SMTP 실패처럼 30초 뒤 다시 시도한다")
    void retriesWhenMemberUnavailable() {
        UUID memberId = UUID.randomUUID();
        contacts.verified.put(memberId, "member@example.com");
        contacts.unavailable = true;
        Notification notification = memberEmail(memberId);

        useCase.execute(new SendEmailCommand(notification.id()));

        assertThat(mailbox.sent).isEmpty();
        assertThat(notification.status()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.isDue(Fakes.NOW.plusSeconds(29))).isFalse();
        assertThat(notification.isDue(Fakes.NOW.plusSeconds(30))).isTrue();

        contacts.unavailable = false;
        useCase.execute(new SendEmailCommand(notification.id()));
        assertThat(mailbox.sent).isEmpty();
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
