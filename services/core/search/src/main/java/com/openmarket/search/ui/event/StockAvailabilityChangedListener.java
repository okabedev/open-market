package com.openmarket.search.ui.event;

import com.openmarket.common.messaging.consumer.IdempotentEventProcessor;
import com.openmarket.search.command.application.ChangeStockAvailabilityCommand;
import com.openmarket.search.command.application.ChangeStockAvailabilityUseCase;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * {@code catalog.stock} 토픽의 {@code StockAvailabilityChanged}를 검색 상품에 반영한다. 같은 토픽의 재고 예약 이벤트(M3)는 처리
 * 기록 없이 넘긴다. 상품 이벤트와 순서가 섞여도 판매 가능 리비전으로 맞춘다.
 */
@Component
class StockAvailabilityChangedListener {

    static final String TYPE = "StockAvailabilityChanged";

    private final IdempotentEventProcessor processor;
    private final ChangeStockAvailabilityUseCase changeAvailability;

    StockAvailabilityChangedListener(
            IdempotentEventProcessor processor, ChangeStockAvailabilityUseCase changeAvailability) {
        this.processor = processor;
        this.changeAvailability = changeAvailability;
    }

    @KafkaListener(topics = "catalog.stock", groupId = "search")
    void on(ConsumerRecord<String, String> record) {
        Header type = record.headers().lastHeader("type");
        String name = type == null ? "" : new String(type.value(), StandardCharsets.UTF_8);
        if (!TYPE.equals(name)) {
            return;
        }
        processor.process(
                "search.stock-availability-changed",
                record,
                StockAvailabilityChangedMessage.class,
                event ->
                        changeAvailability.execute(
                                new ChangeStockAvailabilityCommand(
                                        event.payload().productId(),
                                        event.payload().inStock(),
                                        event.payload().availabilityRevision())));
    }

    /** 소비 쪽 페이로드(docs/architecture/messaging.md "스키마 규칙"). */
    record StockAvailabilityChangedMessage(
            UUID productId, boolean inStock, long availabilityRevision) {}
}
