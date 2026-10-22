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
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * {@code fulfillment.shipment} 토픽에서 {@code ShipmentShipped}(교환 재발송 포함), {@code ShipmentDelivered}를
 * 받아 구매자에게 인앱 알림을 만든다. 그 밖의 이벤트는 처리 기록 없이 넘긴다.
 */
@Component
class ShipmentEventsListener {

    static final String SHIPPED_LISTENER = "notification.shipment-shipped";
    static final String DELIVERED_LISTENER = "notification.shipment-delivered";

    private final IdempotentEventProcessor processor;
    private final NotifyUseCase notify;

    ShipmentEventsListener(IdempotentEventProcessor processor, NotifyUseCase notify) {
        this.processor = processor;
        this.notify = notify;
    }

    @KafkaListener(topics = "fulfillment.shipment", groupId = "notification")
    void on(ConsumerRecord<String, String> record) {
        switch (type(record)) {
            case "ShipmentShipped" ->
                    processor.process(
                            SHIPPED_LISTENER,
                            record,
                            ShipmentShippedMessage.class,
                            event ->
                                    notify.execute(
                                            buyer(
                                                    event.id(),
                                                    NotifyCommand.Kind.SHIPMENT_SHIPPED,
                                                    event.payload().buyerId(),
                                                    event.payload().orderId(),
                                                    Map.of(
                                                            "trackingNumber",
                                                            event.payload().trackingNumber()))));
            case "ShipmentDelivered" ->
                    processor.process(
                            DELIVERED_LISTENER,
                            record,
                            ShipmentDeliveredMessage.class,
                            event ->
                                    notify.execute(
                                            buyer(
                                                    event.id(),
                                                    NotifyCommand.Kind.SHIPMENT_DELIVERED,
                                                    event.payload().buyerId(),
                                                    event.payload().orderId(),
                                                    Map.of())));
            default -> {}
        }
    }

    private static NotifyCommand buyer(
            UUID eventId,
            NotifyCommand.Kind kind,
            UUID buyerId,
            UUID orderId,
            Map<String, String> params) {
        return new NotifyCommand(
                eventId,
                List.of(
                        new NotifyCommand.Notice(
                                kind,
                                NotifyCommand.Audience.BUYER,
                                buyerId,
                                params,
                                NotifyCommand.Target.ORDER,
                                orderId)));
    }

    /** 소비 쪽 페이로드. */
    record ShipmentShippedMessage(UUID orderId, UUID buyerId, String trackingNumber) {}

    /** 소비 쪽 페이로드. */
    record ShipmentDeliveredMessage(UUID orderId, UUID buyerId) {}

    private static String type(ConsumerRecord<String, String> record) {
        Header type = record.headers().lastHeader("type");
        return type == null ? "" : new String(type.value(), StandardCharsets.UTF_8);
    }
}
