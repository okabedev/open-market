package com.openmarket.notification.command.application;

import com.openmarket.notification.command.domain.Notification;
import com.openmarket.notification.command.domain.NotificationRepository;
import java.time.Clock;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이메일 알림 하나를 보낸다. 보낼 차례가 아니면(이미 보냄, 재시도 대기) 아무것도 하지 않는다. SMTP가 실패하면 예외를 던지지 않고 다음 시도로 미룬다.
 *
 * <p>SMTP가 받은 뒤 상태를 저장하기 전에 프로세스가 죽으면 다음 실행에서 한 번 더 보낸다(최소 한 번). 그 사이가 짧아 드물다.
 */
@Service
public class SendEmailUseCase {

    private static final Logger log = LoggerFactory.getLogger(SendEmailUseCase.class);

    private final NotificationRepository notifications;
    private final EmailRendererPort renderer;
    private final MailSenderPort mailSender;
    private final Clock clock;

    public SendEmailUseCase(
            NotificationRepository notifications,
            EmailRendererPort renderer,
            MailSenderPort mailSender,
            Clock clock) {
        this.notifications = notifications;
        this.renderer = renderer;
        this.mailSender = mailSender;
        this.clock = clock;
    }

    @Transactional
    public void execute(SendEmailCommand command) {
        Notification notification = notifications.findById(command.notificationId()).orElse(null);
        if (notification == null || !notification.isDue(clock.instant())) {
            return;
        }
        EmailRendererPort.RenderedEmail email =
                renderer.render(notification.kind(), notification.params());
        try {
            mailSender.send(
                    new MailSenderPort.OutgoingEmail(
                            Objects.requireNonNull(notification.recipientEmail()),
                            email.subject(),
                            email.html(),
                            email.text()));
            notification.markSent(clock.instant());
        } catch (MailSenderPort.MailDeliveryException e) {
            notification.markFailed(clock.instant());
            log.warn(
                    "메일을 보내지 못했다: {} {} ({})",
                    notification.kind(),
                    notification.id(),
                    notification.status());
        }
    }
}
