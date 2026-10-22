package com.openmarket.notification.ui.event;

import com.openmarket.common.messaging.consumer.IdempotentEventProcessor;
import com.openmarket.notification.command.application.NotifyInAppCommand;
import com.openmarket.notification.command.application.NotifyInAppUseCase;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * {@code member.seller} 토픽에서 입점 승인·반려를 받아 신청자에게 인앱 알림을 만든다. 승인은 seller 앱({@code SELLER}), 반려는 아직
 * 판매자가 아니므로 shop 앱({@code BUYER})에 보인다. 그 밖의 이벤트는 처리 기록 없이 넘긴다.
 */
@Component
class SellerEventsListener {

    private final IdempotentEventProcessor processor;
    private final NotifyInAppUseCase notifyInApp;

    SellerEventsListener(IdempotentEventProcessor processor, NotifyInAppUseCase notifyInApp) {
        this.processor = processor;
        this.notifyInApp = notifyInApp;
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
                                    notifyInApp.execute(
                                            one(
                                                    event.id(),
                                                    NotifyInAppCommand.Kind.SELLER_APPROVED,
                                                    NotifyInAppCommand.Audience.SELLER,
                                                    event.payload().sellerId(),
                                                    Map.of(
                                                            "storeName",
                                                            event.payload().storeName()))));
            case "SellerRejected" ->
                    processor.process(
                            "notification.seller-rejected",
                            record,
                            SellerRejectedMessage.class,
                            event ->
                                    notifyInApp.execute(
                                            one(
                                                    event.id(),
                                                    NotifyInAppCommand.Kind.SELLER_REJECTED,
                                                    NotifyInAppCommand.Audience.BUYER,
                                                    event.payload().memberId(),
                                                    Map.of("reason", event.payload().reason()))));
            default -> {}
        }
    }

    private static NotifyInAppCommand one(
            UUID eventId,
            NotifyInAppCommand.Kind kind,
            NotifyInAppCommand.Audience audience,
            UUID recipientId,
            Map<String, String> params) {
        return new NotifyInAppCommand(
                eventId,
                List.of(
                        new NotifyInAppCommand.Notice(
                                kind, audience, recipientId, params, null, null)));
    }

    /** 소비 쪽 페이로드. */
    record SellerApprovedMessage(UUID sellerId, String storeName) {}

    /** 소비 쪽 페이로드. */
    record SellerRejectedMessage(UUID memberId, String reason) {}

    private static String type(ConsumerRecord<String, String> record) {
        Header type = record.headers().lastHeader("type");
        return type == null ? "" : new String(type.value(), StandardCharsets.UTF_8);
    }
}
