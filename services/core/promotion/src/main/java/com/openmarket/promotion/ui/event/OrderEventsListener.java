package com.openmarket.promotion.ui.event;

import com.openmarket.common.messaging.consumer.IdempotentEventProcessor;
import com.openmarket.promotion.command.application.OrderCouponsCommand;
import com.openmarket.promotion.command.application.ReserveCouponsCommand;
import com.openmarket.promotion.command.application.ReserveCouponsUseCase;
import com.openmarket.promotion.command.application.RestoreCouponsUseCase;
import com.openmarket.promotion.command.application.UseCouponsUseCase;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.jspecify.annotations.Nullable;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * {@code order.order} 토픽에서 {@code OrderPlaced}(쿠폰 예약), {@code OrderPaid}(사용 확정), {@code
 * OrderCancelled}(복원)를 받는다(docs/architecture/promotion.md "주문 쿠폰 예약·사용·복원"). 같은 토픽의 다른 이벤트는 처리 기록
 * 없이 넘긴다. 키가 orderId라 한 주문의 이벤트는 순서대로 온다.
 */
@Component
class OrderEventsListener {

    static final String ORDER_PLACED = "OrderPlaced";
    static final String ORDER_PAID = "OrderPaid";
    static final String ORDER_CANCELLED = "OrderCancelled";

    private final IdempotentEventProcessor processor;
    private final ReserveCouponsUseCase reserve;
    private final UseCouponsUseCase use;
    private final RestoreCouponsUseCase restore;

    OrderEventsListener(
            IdempotentEventProcessor processor,
            ReserveCouponsUseCase reserve,
            UseCouponsUseCase use,
            RestoreCouponsUseCase restore) {
        this.processor = processor;
        this.reserve = reserve;
        this.use = use;
        this.restore = restore;
    }

    @KafkaListener(topics = "order.order", groupId = "promotion")
    void on(ConsumerRecord<String, String> record) {
        Header type = record.headers().lastHeader("type");
        String name = type == null ? "" : new String(type.value(), StandardCharsets.UTF_8);
        switch (name) {
            case ORDER_PLACED ->
                    processor.process(
                            "promotion.order-placed",
                            record,
                            OrderPlacedMessage.class,
                            event ->
                                    reserve.execute(
                                            new ReserveCouponsCommand(
                                                    event.payload().orderId(),
                                                    event.payload().buyerId(),
                                                    Objects.requireNonNullElse(
                                                            event.payload().issuedCouponIds(),
                                                            List.of()))));
            case ORDER_PAID ->
                    processor.process(
                            "promotion.order-paid",
                            record,
                            OrderMessage.class,
                            event ->
                                    use.execute(
                                            new OrderCouponsCommand(event.payload().orderId())));
            case ORDER_CANCELLED ->
                    processor.process(
                            "promotion.order-cancelled",
                            record,
                            OrderMessage.class,
                            event ->
                                    restore.execute(
                                            new OrderCouponsCommand(event.payload().orderId())));
            default -> {}
        }
    }

    /**
     * 소비 쪽 페이로드. 발행 쪽 타입을 쓰지 않는다(docs/architecture/messaging.md "스키마 규칙"). 항목은 쓰지 않는다.
     *
     * @param issuedCouponIds M5 전에 낸 {@code OrderPlaced}에는 없다(쿠폰 없음)
     */
    record OrderPlacedMessage(UUID orderId, UUID buyerId, @Nullable List<UUID> issuedCouponIds) {}

    /** {@code OrderPaid}, {@code OrderCancelled}. orderId만 쓴다. */
    record OrderMessage(UUID orderId) {}
}
