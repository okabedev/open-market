package com.openmarket.auth.ui.event;

import com.openmarket.auth.command.application.DeactivateAccountCommand;
import com.openmarket.auth.command.application.DeactivateAccountUseCase;
import com.openmarket.common.messaging.consumer.IdempotentEventProcessor;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** {@code member.member} 토픽에서 {@code MemberWithdrawn}을 받아 계정을 비활성화한다. 다른 이벤트는 처리 기록 없이 넘긴다. */
@Component
class MemberEventsListener {

    private final IdempotentEventProcessor processor;
    private final DeactivateAccountUseCase deactivateAccount;

    MemberEventsListener(
            IdempotentEventProcessor processor, DeactivateAccountUseCase deactivateAccount) {
        this.processor = processor;
        this.deactivateAccount = deactivateAccount;
    }

    @KafkaListener(topics = "member.member", groupId = "auth")
    void on(ConsumerRecord<String, String> record) {
        Header type = record.headers().lastHeader("type");
        if (type == null
                || !"MemberWithdrawn".equals(new String(type.value(), StandardCharsets.UTF_8))) {
            return;
        }
        processor.process(
                "auth.member-withdrawn",
                record,
                MemberWithdrawnMessage.class,
                event ->
                        deactivateAccount.execute(
                                new DeactivateAccountCommand(event.payload().memberId())));
    }

    /** 소비 쪽 페이로드(docs/architecture/messaging.md "스키마 규칙"). */
    record MemberWithdrawnMessage(UUID memberId) {}
}
