package com.openmarket.catalog.ui.event;

import com.openmarket.catalog.command.application.RegisterSellerCommand;
import com.openmarket.catalog.command.application.RegisterSellerUseCase;
import com.openmarket.common.messaging.consumer.IdempotentEventProcessor;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * {@code member.seller} 토픽에서 {@code SellerApproved}(판매자 사본 생성)를 받는다. 정지·해제는 판매 상태 기능에서 더한다. 같은 토픽의
 * 다른 이벤트는 처리 기록 없이 넘긴다.
 */
@Component
class SellerEventsListener {

    static final String SELLER_APPROVED = "SellerApproved";

    private final IdempotentEventProcessor processor;
    private final RegisterSellerUseCase registerSeller;

    SellerEventsListener(IdempotentEventProcessor processor, RegisterSellerUseCase registerSeller) {
        this.processor = processor;
        this.registerSeller = registerSeller;
    }

    @KafkaListener(topics = "member.seller", groupId = "catalog")
    void on(ConsumerRecord<String, String> record) {
        Header type = record.headers().lastHeader("type");
        String name = type == null ? "" : new String(type.value(), StandardCharsets.UTF_8);
        if (SELLER_APPROVED.equals(name)) {
            processor.process(
                    "catalog.seller-approved",
                    record,
                    SellerApprovedMessage.class,
                    event ->
                            registerSeller.execute(
                                    new RegisterSellerCommand(
                                            event.payload().sellerId(),
                                            event.payload().storeName())));
        }
    }

    /** 소비 쪽 페이로드. 발행 쪽 타입을 쓰지 않는다(docs/architecture/messaging.md "스키마 규칙"). */
    record SellerApprovedMessage(UUID sellerId, String storeName) {}
}
