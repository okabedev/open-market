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
 * {@code promotion.issued-coupon} 토픽에서 쿠폰 예약 결과 {@code CouponReserved}(결과 기록, 재고도 끝났으면 결제 대기),
 * {@code CouponReservationFailed}(취소)를 받는다(docs/architecture/order.md "예약 결과"). 같은 토픽의 다른 이벤트는 처리
 * 기록 없이 넘긴다.
 */
@Component
class CouponEventsListener {

    static final String COUPON_RESERVED = "CouponReserved";
    static final String COUPON_RESERVATION_FAILED = "CouponReservationFailed";

    private final IdempotentEventProcessor processor;
    private final AwaitPaymentUseCase awaitPayment;
    private final CancelOrderUseCase cancel;

    CouponEventsListener(
            IdempotentEventProcessor processor,
            AwaitPaymentUseCase awaitPayment,
            CancelOrderUseCase cancel) {
        this.processor = processor;
        this.awaitPayment = awaitPayment;
        this.cancel = cancel;
    }

    @KafkaListener(topics = "promotion.issued-coupon", groupId = "order")
    void on(ConsumerRecord<String, String> record) {
        Header type = record.headers().lastHeader("type");
        String name = type == null ? "" : new String(type.value(), StandardCharsets.UTF_8);
        switch (name) {
            case COUPON_RESERVED ->
                    processor.process(
                            "order.coupon-reserved",
                            record,
                            ReservationMessage.class,
                            event ->
                                    awaitPayment.execute(
                                            new AwaitPaymentCommand(
                                                    event.payload().orderId(),
                                                    AwaitPaymentCommand.Reservation.COUPON)));
            case COUPON_RESERVATION_FAILED ->
                    processor.process(
                            "order.coupon-reservation-failed",
                            record,
                            ReservationMessage.class,
                            event ->
                                    cancel.execute(
                                            new CancelOrderCommand(
                                                    event.payload().orderId(),
                                                    CancelOrderCommand.Reason.COUPON_UNAVAILABLE)));
            default -> {}
        }
    }

    /** 소비 쪽 페이로드. 쿠폰 목록은 쓰지 않는다(화면은 주문 상세의 취소 사유로 알린다). */
    record ReservationMessage(UUID orderId) {}
}
