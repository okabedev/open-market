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
 * {@code fulfillment.shipment} 토픽에서 {@code ShipmentShipped}(교환 재발송 포함), {@code ShipmentDelivered}를
 * 받아 구매자에게 인앱 알림을 만든다. 그 밖의 이벤트는 처리 기록 없이 넘긴다.
 */
@Component
class ShipmentEventsListener {

    static final String SHIPPED_LISTENER = "notification.shipment-shipped";
    static final String DELIVERED_LISTENER = "notification.shipment-delivered";

    private final IdempotentEventProcessor processor;
    private final NotifyInAppUseCase notifyInApp;

    ShipmentEventsListener(IdempotentEventProcessor processor, NotifyInAppUseCase notifyInApp) {
        this.processor = processor;
        this.notifyInApp = notifyInApp;
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
                                    notifyInApp.execute(
                                            buyer(
                                                    event.id(),
                                                    NotifyInAppCommand.Kind.SHIPMENT_SHIPPED,
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
                                    notifyInApp.execute(
                                            buyer(
                                                    event.id(),
                                                    NotifyInAppCommand.Kind.SHIPMENT_DELIVERED,
                                                    event.payload().buyerId(),
                                                    event.payload().orderId(),
                                                    Map.of())));
            default -> {}
        }
    }

    private static NotifyInAppCommand buyer(
            UUID eventId,
            NotifyInAppCommand.Kind kind,
            UUID buyerId,
            UUID orderId,
            Map<String, String> params) {
        return new NotifyInAppCommand(
                eventId,
                List.of(
                        new NotifyInAppCommand.Notice(
                                kind,
                                NotifyInAppCommand.Audience.BUYER,
                                buyerId,
                                params,
                                NotifyInAppCommand.Target.ORDER,
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
