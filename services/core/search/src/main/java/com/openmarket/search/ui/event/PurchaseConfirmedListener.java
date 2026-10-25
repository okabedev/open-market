package com.openmarket.search.ui.event;

import com.openmarket.common.messaging.consumer.IdempotentEventProcessor;
import com.openmarket.search.command.application.AddProductSalesCommand;
import com.openmarket.search.command.application.AddProductSalesUseCase;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * {@code fulfillment.shipment} 토픽의 {@code PurchaseConfirmed}로 상품 판매량을
 * 더한다(docs/architecture/search.md "판매량"). 같은 토픽의 다른 이벤트는 처리 기록 없이 넘긴다.
 */
@Component
class PurchaseConfirmedListener {

    static final String TYPE = "PurchaseConfirmed";

    private final IdempotentEventProcessor processor;
    private final AddProductSalesUseCase addSales;

    PurchaseConfirmedListener(IdempotentEventProcessor processor, AddProductSalesUseCase addSales) {
        this.processor = processor;
        this.addSales = addSales;
    }

    @KafkaListener(topics = "fulfillment.shipment", groupId = "search")
    void on(ConsumerRecord<String, String> record) {
        Header type = record.headers().lastHeader("type");
        String name = type == null ? "" : new String(type.value(), StandardCharsets.UTF_8);
        if (!TYPE.equals(name)) {
            return;
        }
        processor.process(
                "search.purchase-confirmed",
                record,
                PurchaseConfirmedMessage.class,
                event ->
                        addSales.execute(
                                new AddProductSalesCommand(
                                        event.payload().sellerOrderId(),
                                        event.payload().lines().stream()
                                                .map(
                                                        line ->
                                                                new AddProductSalesCommand.Line(
                                                                        line.productId(),
                                                                        line.quantity()))
                                                .toList())));
    }

    /** 소비 쪽 페이로드(docs/architecture/messaging.md "스키마 규칙"). 판매량에 쓰는 필드만 읽는다. */
    record PurchaseConfirmedMessage(UUID sellerOrderId, List<Line> lines) {}

    record Line(UUID productId, int quantity) {}
}
