package com.openmarket.member.ui.event;

import com.openmarket.common.messaging.consumer.IdempotentEventProcessor;
import com.openmarket.member.command.application.RegisterMemberCommand;
import com.openmarket.member.command.application.RegisterMemberUseCase;
import com.openmarket.member.command.application.VerifyMemberEmailCommand;
import com.openmarket.member.command.application.VerifyMemberEmailUseCase;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * {@code auth.account} 토픽에서 {@code AccountRegistered}(회원 생성)와 {@code AccountEmailVerified}(인증 기록)를
 * 받는다. 같은 토픽의 다른 이벤트({@code EmailVerificationRequested})는 처리 기록 없이 넘긴다.
 *
 * <p>키가 accountId라 한 계정의 이벤트는 순서대로 온다. 인증 이벤트가 회원보다 먼저 처리되는 일은 가입 이벤트가 DLT로 간 경우뿐이고, 이때 인증 이벤트도 실패해
 * DLT로 간다.
 */
@Component
class AccountEventsListener {

    static final String ACCOUNT_REGISTERED = "AccountRegistered";
    static final String ACCOUNT_EMAIL_VERIFIED = "AccountEmailVerified";

    private final IdempotentEventProcessor processor;
    private final RegisterMemberUseCase registerMember;
    private final VerifyMemberEmailUseCase verifyMemberEmail;

    AccountEventsListener(
            IdempotentEventProcessor processor,
            RegisterMemberUseCase registerMember,
            VerifyMemberEmailUseCase verifyMemberEmail) {
        this.processor = processor;
        this.registerMember = registerMember;
        this.verifyMemberEmail = verifyMemberEmail;
    }

    @KafkaListener(topics = "auth.account", groupId = "member")
    void on(ConsumerRecord<String, String> record) {
        Header type = record.headers().lastHeader("type");
        String name = type == null ? "" : new String(type.value(), StandardCharsets.UTF_8);
        switch (name) {
            case ACCOUNT_REGISTERED ->
                    processor.process(
                            "member.account-registered",
                            record,
                            AccountRegisteredMessage.class,
                            event ->
                                    registerMember.execute(
                                            new RegisterMemberCommand(
                                                    event.payload().accountId(),
                                                    event.payload().email(),
                                                    event.payload().name())));
            case ACCOUNT_EMAIL_VERIFIED ->
                    processor.process(
                            "member.account-email-verified",
                            record,
                            AccountEmailVerifiedMessage.class,
                            event ->
                                    verifyMemberEmail.execute(
                                            new VerifyMemberEmailCommand(
                                                    event.payload().accountId())));
            default -> {}
        }
    }

    /** 소비 쪽 페이로드. 발행 쪽 타입을 쓰지 않는다(docs/architecture/messaging.md "스키마 규칙"). */
    record AccountRegisteredMessage(UUID accountId, String email, String name) {

        /** 개인정보를 남기지 않는다(NFR-7). */
        @Override
        public String toString() {
            return "AccountRegisteredMessage[accountId=" + accountId + "]";
        }
    }

    record AccountEmailVerifiedMessage(UUID accountId) {}
}
