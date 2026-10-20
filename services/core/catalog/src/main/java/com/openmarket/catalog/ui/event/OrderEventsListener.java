package com.openmarket.catalog.ui.event;

import com.openmarket.catalog.command.application.CommitStockReservationUseCase;
import com.openmarket.catalog.command.application.ReleaseStockReservationUseCase;
import com.openmarket.catalog.command.application.ReserveStockCommand;
import com.openmarket.catalog.command.application.ReserveStockUseCase;
import com.openmarket.catalog.command.application.StockReservationCommand;
import com.openmarket.common.messaging.consumer.IdempotentEventProcessor;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * {@code order.order} 토픽에서 {@code OrderPlaced}(재고 예약), {@code OrderPaid}(예약 확정), {@code
 * OrderCancelled}(예약 해제)를 받는다(docs/architecture/catalog.md "재고 예약"). 같은 토픽의 다른 이벤트는 처리 기록 없이 넘긴다.
 * 키가 orderId라 한 주문의 이벤트는 순서대로 온다.
 */
@Component
class OrderEventsListener {

    static final String ORDER_PLACED = "OrderPlaced";
    static final String ORDER_PAID = "OrderPaid";
    static final String ORDER_CANCELLED = "OrderCancelled";

    private final IdempotentEventProcessor processor;
    private final ReserveStockUseCase reserve;
    private final CommitStockReservationUseCase commit;
    private final ReleaseStockReservationUseCase release;

    OrderEventsListener(
            IdempotentEventProcessor processor,
            ReserveStockUseCase reserve,
            CommitStockReservationUseCase commit,
            ReleaseStockReservationUseCase release) {
        this.processor = processor;
        this.reserve = reserve;
        this.commit = commit;
        this.release = release;
    }

    @KafkaListener(topics = "order.order", groupId = "catalog")
    void on(ConsumerRecord<String, String> record) {
        Header type = record.headers().lastHeader("type");
        String name = type == null ? "" : new String(type.value(), StandardCharsets.UTF_8);
        switch (name) {
            case ORDER_PLACED ->
                    processor.process(
                            "catalog.order-placed",
                            record,
                            OrderPlacedMessage.class,
                            event ->
                                    reserve.execute(
                                            new ReserveStockCommand(
                                                    event.payload().orderId(),
                                                    event.payload().lines().stream()
                                                            .map(
                                                                    line ->
                                                                            new ReserveStockCommand
                                                                                    .Line(
                                                                                    line.skuId(),
                                                                                    line
                                                                                            .quantity()))
                                                            .toList())));
            case ORDER_PAID ->
                    processor.process(
                            "catalog.order-paid",
                            record,
                            OrderMessage.class,
                            event ->
                                    commit.execute(
                                            new StockReservationCommand(
                                                    event.payload().orderId())));
            case ORDER_CANCELLED ->
                    processor.process(
                            "catalog.order-cancelled",
                            record,
                            OrderMessage.class,
                            event ->
                                    release.execute(
                                            new StockReservationCommand(
                                                    event.payload().orderId())));
            default -> {}
        }
    }

    /** 소비 쪽 페이로드. 발행 쪽 타입을 쓰지 않는다(docs/architecture/messaging.md "스키마 규칙"). 쿠폰은 쓰지 않는다. */
    record OrderPlacedMessage(UUID orderId, List<Line> lines) {

        record Line(UUID skuId, int quantity) {}
    }

    /** {@code OrderPaid}, {@code OrderCancelled}. orderId만 쓴다. */
    record OrderMessage(UUID orderId) {}
}
