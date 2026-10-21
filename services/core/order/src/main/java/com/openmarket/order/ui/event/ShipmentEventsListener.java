package com.openmarket.order.ui.event;

import com.openmarket.common.messaging.consumer.IdempotentEventProcessor;
import com.openmarket.order.command.application.AdvanceSellerOrderCommand;
import com.openmarket.order.command.application.AdvanceSellerOrderUseCase;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * {@code fulfillment.shipment} 토픽의 배송 이벤트로 하위 주문 진행 상태를 바꾼다(docs/architecture/order.md "하위 주문 진행
 * 상태"). 구매확정({@code PurchaseConfirmed})도 진행 상태다. 그 밖의 이벤트는 처리 기록 없이 넘긴다.
 */
@Component
class ShipmentEventsListener {

    /**
     * 이벤트 이름 → 진행 단계. 리스너 이름은 {@code order.shipment-<단계>}다(예: {@code order.shipment-in-transit},
     * 구매확정은 {@code order.shipment-confirmed}).
     */
    static final Map<String, AdvanceSellerOrderCommand.Stage> STAGES =
            Map.of(
                    "ShipmentAccepted", AdvanceSellerOrderCommand.Stage.ACCEPTED,
                    "ShipmentShipped", AdvanceSellerOrderCommand.Stage.SHIPPED,
                    "ShipmentInTransit", AdvanceSellerOrderCommand.Stage.IN_TRANSIT,
                    "ShipmentDelivered", AdvanceSellerOrderCommand.Stage.DELIVERED,
                    "PurchaseConfirmed", AdvanceSellerOrderCommand.Stage.CONFIRMED);

    private final IdempotentEventProcessor processor;
    private final AdvanceSellerOrderUseCase advance;

    ShipmentEventsListener(IdempotentEventProcessor processor, AdvanceSellerOrderUseCase advance) {
        this.processor = processor;
        this.advance = advance;
    }

    @KafkaListener(topics = "fulfillment.shipment", groupId = "order")
    void on(ConsumerRecord<String, String> record) {
        Header type = record.headers().lastHeader("type");
        String name = type == null ? "" : new String(type.value(), StandardCharsets.UTF_8);
        AdvanceSellerOrderCommand.Stage stage = STAGES.get(name);
        if (stage == null) {
            return;
        }
        processor.process(
                "order.shipment-" + stage.name().toLowerCase(Locale.ROOT).replace('_', '-'),
                record,
                ShipmentMessage.class,
                event ->
                        advance.execute(
                                new AdvanceSellerOrderCommand(
                                        event.payload().orderId(),
                                        event.payload().sellerOrderId(),
                                        stage)));
    }

    /** 소비 쪽 페이로드. 네 이벤트 모두 이 두 필드를 갖는다. */
    record ShipmentMessage(UUID orderId, UUID sellerOrderId) {}
}
