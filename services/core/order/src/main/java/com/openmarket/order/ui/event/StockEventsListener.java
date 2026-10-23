package com.openmarket.order.ui.event;

import com.openmarket.common.messaging.consumer.IdempotentEventProcessor;
import com.openmarket.order.command.application.AwaitPaymentCommand;
import com.openmarket.order.command.application.AwaitPaymentUseCase;
import com.openmarket.order.command.application.CancelOrderCommand;
import com.openmarket.order.command.application.CancelOrderUseCase;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * {@code catalog.stock} 토픽에서 재고 예약 결과 {@code StockReserved}(결제 대기), {@code
 * StockReservationFailed}(취소)를 받는다(docs/architecture/order.md "예약 결과"). 같은 토픽의 {@code
 * StockAvailabilityChanged}는 처리 기록 없이 넘긴다.
 */
@Component
class StockEventsListener {

    static final String STOCK_RESERVED = "StockReserved";
    static final String STOCK_RESERVATION_FAILED = "StockReservationFailed";

    private final IdempotentEventProcessor processor;
    private final AwaitPaymentUseCase awaitPayment;
    private final CancelOrderUseCase cancel;

    StockEventsListener(
            IdempotentEventProcessor processor,
            AwaitPaymentUseCase awaitPayment,
            CancelOrderUseCase cancel) {
        this.processor = processor;
        this.awaitPayment = awaitPayment;
        this.cancel = cancel;
    }

    @KafkaListener(topics = "catalog.stock", groupId = "order")
    void on(ConsumerRecord<String, String> record) {
        Header type = record.headers().lastHeader("type");
        String name = type == null ? "" : new String(type.value(), StandardCharsets.UTF_8);
        switch (name) {
            case STOCK_RESERVED ->
                    processor.process(
                            "order.stock-reserved",
                            record,
                            ReservationMessage.class,
                            event ->
                                    awaitPayment.execute(
                                            new AwaitPaymentCommand(
                                                    event.payload().orderId(),
                                                    AwaitPaymentCommand.Reservation.STOCK)));
            case STOCK_RESERVATION_FAILED ->
                    processor.process(
                            "order.stock-reservation-failed",
                            record,
                            ReservationMessage.class,
                            event ->
                                    cancel.execute(
                                            new CancelOrderCommand(
                                                    event.payload().orderId(),
                                                    CancelOrderCommand.Reason.STOCK_UNAVAILABLE)));
            default -> {}
        }
    }

    /** 소비 쪽 페이로드. 모자란 SKU 목록은 쓰지 않는다(화면은 주문 상세의 취소 사유로 알린다). */
    record ReservationMessage(UUID orderId) {}
}
