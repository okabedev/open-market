package com.openmarket.notification.command.application;

/** SMTP로 메일을 보낸다. 구현은 Spring Mail이고 로컬은 Mailpit이다. */
public interface MailSenderPort {

    /**
     * 보낸다. 실패하면 {@link MailDeliveryException}을 던진다.
     *
     * @throws MailDeliveryException SMTP 서버가 받지 않았다. 다시 시도할 수 있다
     */
    void send(OutgoingEmail email);

    /** 보낼 메일. HTML과 텍스트 본문을 함께 보낸다. */
    record OutgoingEmail(String to, String subject, String html, String text) {

        @Override
        public String toString() {
            return "OutgoingEmail[subject=" + subject + "]";
        }
    }

    /** 메일을 보내지 못했다. 메시지에 받는 사람이나 본문을 넣지 않는다. */
    class MailDeliveryException extends RuntimeException {

        public MailDeliveryException(String message) {
            super(message);
        }

        public MailDeliveryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
