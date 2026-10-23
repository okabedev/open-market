package com.openmarket.review.ui.event;

import com.openmarket.common.messaging.consumer.IdempotentEventProcessor;
import com.openmarket.review.command.application.CreateReviewEligibilitiesCommand;
import com.openmarket.review.command.application.CreateReviewEligibilitiesUseCase;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * {@code fulfillment.shipment} 토픽에서 {@code PurchaseConfirmed}를 받아 주문 항목마다 리뷰 작성 자격을 만든다
 * (docs/architecture/review.md "리뷰 작성 자격"). 같은 토픽의 다른 이벤트는 처리 기록 없이 넘긴다.
 */
@Component
class ShipmentEventsListener {

    static final String PURCHASE_CONFIRMED = "PurchaseConfirmed";

    private final IdempotentEventProcessor processor;
    private final CreateReviewEligibilitiesUseCase createEligibilities;

    ShipmentEventsListener(
            IdempotentEventProcessor processor,
            CreateReviewEligibilitiesUseCase createEligibilities) {
        this.processor = processor;
        this.createEligibilities = createEligibilities;
    }

    @KafkaListener(topics = "fulfillment.shipment", groupId = "review")
    void on(ConsumerRecord<String, String> record) {
        Header type = record.headers().lastHeader("type");
        String name = type == null ? "" : new String(type.value(), StandardCharsets.UTF_8);
        if (!PURCHASE_CONFIRMED.equals(name)) {
            return;
        }
        processor.process(
                "review.purchase-confirmed",
                record,
                PurchaseConfirmedMessage.class,
                event -> createEligibilities.execute(event.payload().toCommand()));
    }

    /** 소비 쪽 페이로드. 발행 쪽 타입을 쓰지 않는다(docs/architecture/messaging.md "스키마 규칙"). 작성 자격에 쓰는 필드만 읽는다. */
    record PurchaseConfirmedMessage(
            UUID orderId,
            UUID sellerOrderId,
            UUID sellerId,
            UUID buyerId,
            Instant confirmedAt,
            List<Line> lines) {

        CreateReviewEligibilitiesCommand toCommand() {
            return new CreateReviewEligibilitiesCommand(
                    orderId,
                    sellerOrderId,
                    sellerId,
                    buyerId,
                    confirmedAt,
                    lines.stream()
                            .map(
                                    line ->
                                            new CreateReviewEligibilitiesCommand.Line(
                                                    line.orderLineId(),
                                                    line.productId(),
                                                    line.productName(),
                                                    line.optionValues()))
                            .toList());
        }
    }

    record Line(UUID orderLineId, UUID productId, String productName, List<String> optionValues) {}
}
