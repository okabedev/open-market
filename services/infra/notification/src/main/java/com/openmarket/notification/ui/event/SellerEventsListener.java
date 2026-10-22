package com.openmarket.notification.ui.event;

import com.openmarket.common.messaging.consumer.IdempotentEventProcessor;
import com.openmarket.notification.command.application.NotifyCommand;
import com.openmarket.notification.command.application.NotifyUseCase;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.jspecify.annotations.Nullable;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * {@code member.seller} 토픽에서 입점 승인·반려를 받아 신청자에게 인앱 알림을 만든다. 승인은 seller 앱({@code SELLER}), 반려는 아직
 * 판매자가 아니므로 shop 앱({@code BUYER})에 보인다. 그 밖의 이벤트는 처리 기록 없이 넘긴다.
 */
@Component
class SellerEventsListener {

    private final IdempotentEventProcessor processor;
    private final NotifyUseCase notify;

    SellerEventsListener(IdempotentEventProcessor processor, NotifyUseCase notify) {
        this.processor = processor;
        this.notify = notify;
    }

    @KafkaListener(topics = "member.seller", groupId = "notification")
    void on(ConsumerRecord<String, String> record) {
        switch (type(record)) {
            case "SellerApproved" ->
                    processor.process(
                            "notification.seller-approved",
                            record,
                            SellerApprovedMessage.class,
                            event ->
                                    notify.execute(
                                            one(
                                                    event.id(),
                                                    NotifyCommand.Kind.SELLER_APPROVED,
                                                    NotifyCommand.Audience.SELLER,
                                                    event.payload().sellerId(),
                                                    Map.of(
                                                            "storeName",
                                                            event.payload().storeName()),
                                                    null,
                                                    null)));
            case "SellerRejected" ->
                    processor.process(
                            "notification.seller-rejected",
                            record,
                            SellerRejectedMessage.class,
                            event ->
                                    notify.execute(
                                            one(
                                                    event.id(),
                                                    NotifyCommand.Kind.SELLER_REJECTED,
                                                    NotifyCommand.Audience.BUYER,
                                                    event.payload().memberId(),
                                                    Map.of("reason", event.payload().reason()),
                                                    NotifyCommand.Target.SELLER_APPLICATION,
                                                    event.payload().applicationId())));
            default -> {}
        }
    }

    private static NotifyCommand one(
            UUID eventId,
            NotifyCommand.Kind kind,
            NotifyCommand.Audience audience,
            UUID recipientId,
            Map<String, String> params,
            NotifyCommand.@Nullable Target target,
            @Nullable UUID targetId) {
        return new NotifyCommand(
                eventId,
                List.of(
                        new NotifyCommand.Notice(
                                kind, audience, recipientId, params, target, targetId)));
    }

    /** 소비 쪽 페이로드. */
    record SellerApprovedMessage(UUID sellerId, String storeName) {}

    /** 소비 쪽 페이로드. */
    record SellerRejectedMessage(UUID memberId, UUID applicationId, String reason) {}

    private static String type(ConsumerRecord<String, String> record) {
        Header type = record.headers().lastHeader("type");
        return type == null ? "" : new String(type.value(), StandardCharsets.UTF_8);
    }
}
