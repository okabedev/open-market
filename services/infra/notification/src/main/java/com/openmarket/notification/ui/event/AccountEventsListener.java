package com.openmarket.notification.ui.event;

import com.openmarket.common.messaging.consumer.IdempotentEventProcessor;
import com.openmarket.notification.command.application.NotifyEmailVerificationCommand;
import com.openmarket.notification.command.application.NotifyEmailVerificationUseCase;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * {@code auth.account} 토픽에서 {@code EmailVerificationRequested}만 받아 인증 메일 알림을 만든다. 같은 토픽의 다른 이벤트(
 * {@code AccountRegistered}, {@code AccountEmailVerified})는 처리 기록 없이 넘긴다.
 */
@Component
class AccountEventsListener {

    static final String LISTENER = "notification.email-verification-requested";
    static final String EMAIL_VERIFICATION_REQUESTED = "EmailVerificationRequested";

    private final IdempotentEventProcessor processor;
    private final NotifyEmailVerificationUseCase notifyEmailVerification;

    AccountEventsListener(
            IdempotentEventProcessor processor,
            NotifyEmailVerificationUseCase notifyEmailVerification) {
        this.processor = processor;
        this.notifyEmailVerification = notifyEmailVerification;
    }

    @KafkaListener(topics = "auth.account", groupId = "notification")
    void on(ConsumerRecord<String, String> record) {
        Header type = record.headers().lastHeader("type");
        if (type == null
                || !EMAIL_VERIFICATION_REQUESTED.equals(
                        new String(type.value(), StandardCharsets.UTF_8))) {
            return;
        }
        processor.process(
                LISTENER,
                record,
                EmailVerificationRequestedMessage.class,
                event ->
                        notifyEmailVerification.execute(
                                new NotifyEmailVerificationCommand(
                                        event.id(),
                                        event.payload().accountId(),
                                        event.payload().email(),
                                        event.payload().verificationUrl(),
                                        event.payload().expiresAt())));
    }

    /**
     * 소비 쪽 페이로드. 발행 쪽 타입을 쓰지 않는다(docs/architecture/messaging.md "스키마 규칙").
     *
     * @param verificationUrl 일회용 토큰이 든 링크. 로그에 남기지 않는다
     */
    record EmailVerificationRequestedMessage(
            UUID accountId, String email, String verificationUrl, Instant expiresAt) {

        @Override
        public String toString() {
            return "EmailVerificationRequestedMessage[accountId=" + accountId + "]";
        }
    }
}
