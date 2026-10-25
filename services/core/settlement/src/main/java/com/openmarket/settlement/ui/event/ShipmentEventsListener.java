package com.openmarket.settlement.ui.event;

import com.openmarket.common.messaging.consumer.IdempotentEventProcessor;
import com.openmarket.settlement.command.application.CreateSettlementItemCommand;
import com.openmarket.settlement.command.application.CreateSettlementItemUseCase;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * {@code fulfillment.shipment} 토픽에서 {@code PurchaseConfirmed}를 받아 하위 주문마다 정산 대상을 만든다
 * (docs/architecture/settlement.md "정산 대상"). 같은 토픽의 다른 이벤트는 처리 기록 없이 넘긴다.
 */
@Component
class ShipmentEventsListener {

    static final String PURCHASE_CONFIRMED = "PurchaseConfirmed";

    private final IdempotentEventProcessor processor;
    private final CreateSettlementItemUseCase createItem;

    ShipmentEventsListener(
            IdempotentEventProcessor processor, CreateSettlementItemUseCase createItem) {
        this.processor = processor;
        this.createItem = createItem;
    }

    @KafkaListener(topics = "fulfillment.shipment", groupId = "settlement")
    void on(ConsumerRecord<String, String> record) {
        Header type = record.headers().lastHeader("type");
        String name = type == null ? "" : new String(type.value(), StandardCharsets.UTF_8);
        if (!PURCHASE_CONFIRMED.equals(name)) {
            return;
        }
        processor.process(
                "settlement.purchase-confirmed",
                record,
                PurchaseConfirmedMessage.class,
                event -> createItem.execute(event.payload().toCommand()));
    }

    /** 소비 쪽 페이로드. 발행 쪽 타입을 쓰지 않는다(docs/architecture/messaging.md "스키마 규칙"). 정산에 쓰는 필드만 읽는다. */
    record PurchaseConfirmedMessage(
            UUID orderId,
            UUID sellerOrderId,
            UUID sellerId,
            Instant confirmedAt,
            long sellerCouponDiscount,
            List<Line> lines) {

        CreateSettlementItemCommand toCommand() {
            return new CreateSettlementItemCommand(
                    orderId,
                    sellerOrderId,
                    sellerId,
                    confirmedAt,
                    sellerCouponDiscount,
                    lines.stream()
                            .map(
                                    line ->
                                            new CreateSettlementItemCommand.Line(
                                                    line.orderLineId(),
                                                    line.productId(),
                                                    line.productName(),
                                                    line.categoryId(),
                                                    line.quantity(),
                                                    line.amount()))
                            .toList());
        }
    }

    record Line(
            UUID orderLineId,
            UUID productId,
            String productName,
            UUID categoryId,
            int quantity,
            long amount) {}
}
