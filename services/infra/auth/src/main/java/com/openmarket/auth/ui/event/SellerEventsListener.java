package com.openmarket.auth.ui.event;

import com.openmarket.auth.command.application.ChangeSellerRoleCommand;
import com.openmarket.auth.command.application.ChangeSellerRoleUseCase;
import com.openmarket.common.messaging.consumer.IdempotentEventProcessor;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * {@code member.seller} 토픽에서 판매자 역할을 바꾼다. {@code SellerApproved}, {@code SellerReinstated}는 {@code
 * SELLER}를 붙이고 {@code SellerSuspended}는 뗀다. {@code SellerRejected}는 처리 기록 없이 넘긴다.
 *
 * <p>키가 sellerId라 한 판매자의 이벤트는 순서대로 온다.
 */
@Component
class SellerEventsListener {

    private final IdempotentEventProcessor processor;
    private final ChangeSellerRoleUseCase changeSellerRole;

    SellerEventsListener(
            IdempotentEventProcessor processor, ChangeSellerRoleUseCase changeSellerRole) {
        this.processor = processor;
        this.changeSellerRole = changeSellerRole;
    }

    @KafkaListener(topics = "member.seller", groupId = "auth")
    void on(ConsumerRecord<String, String> record) {
        Header type = record.headers().lastHeader("type");
        String name = type == null ? "" : new String(type.value(), StandardCharsets.UTF_8);
        switch (name) {
            case "SellerApproved" -> change("auth.seller-approved", record, true);
            case "SellerReinstated" -> change("auth.seller-reinstated", record, true);
            case "SellerSuspended" -> change("auth.seller-suspended", record, false);
            default -> {}
        }
    }

    private void change(String listener, ConsumerRecord<String, String> record, boolean seller) {
        processor.process(
                listener,
                record,
                SellerMessage.class,
                event ->
                        changeSellerRole.execute(
                                new ChangeSellerRoleCommand(event.payload().sellerId(), seller)));
    }

    /** 소비 쪽 페이로드. 세 이벤트에서 sellerId만 읽는다(docs/architecture/messaging.md "스키마 규칙"). */
    record SellerMessage(UUID sellerId) {}
}
