package com.openmarket.notification.infra.mail;

import com.openmarket.notification.command.application.MailSenderPort;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/** Spring Mail로 SMTP 발송한다. 로컬은 Mailpit(1025)이다. */
public class SpringMailSender implements MailSenderPort {

    private final JavaMailSender mailSender;
    private final String from;

    public SpringMailSender(JavaMailSender mailSender, String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(OutgoingEmail email) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(email.to());
            helper.setSubject(email.subject());
            helper.setText(email.text(), email.html());
            mailSender.send(message);
        } catch (MailException | MessagingException e) {
            throw new MailDeliveryException("SMTP 발송 실패: " + e.getClass().getSimpleName(), e);
        }
    }
}
