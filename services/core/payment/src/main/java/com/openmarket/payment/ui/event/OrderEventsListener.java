package com.openmarket.payment.ui.event;

import com.openmarket.common.messaging.consumer.IdempotentEventProcessor;
import com.openmarket.payment.command.application.PreparePaymentCommand;
import com.openmarket.payment.command.application.PreparePaymentUseCase;
import com.openmarket.payment.command.application.RequestPaymentCancelCommand;
import com.openmarket.payment.command.application.RequestPaymentCancelUseCase;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * {@code order.order} 토픽에서 {@code OrderAwaitingPayment}(결제 준비), {@code OrderCancelled}(결제 무효화·보상 취소
 * 요청)를 받는다(docs/architecture/payment.md "결제 준비", "보상 취소"). 같은 토픽의 다른 이벤트는 처리 기록 없이 넘긴다.
 */
@Component
class OrderEventsListener {

    static final String ORDER_AWAITING_PAYMENT = "OrderAwaitingPayment";
    static final String ORDER_CANCELLED = "OrderCancelled";

    private final IdempotentEventProcessor processor;
    private final PreparePaymentUseCase prepare;
    private final RequestPaymentCancelUseCase requestCancel;

    OrderEventsListener(
            IdempotentEventProcessor processor,
            PreparePaymentUseCase prepare,
            RequestPaymentCancelUseCase requestCancel) {
        this.processor = processor;
        this.prepare = prepare;
        this.requestCancel = requestCancel;
    }

    @KafkaListener(topics = "order.order", groupId = "payment")
    void on(ConsumerRecord<String, String> record) {
        Header type = record.headers().lastHeader("type");
        String name = type == null ? "" : new String(type.value(), StandardCharsets.UTF_8);
        if (ORDER_CANCELLED.equals(name)) {
            processor.process(
                    "payment.order-cancelled",
                    record,
                    CancelledMessage.class,
                    event ->
                            requestCancel.execute(
                                    new RequestPaymentCancelCommand(event.payload().orderId())));
        } else if (ORDER_AWAITING_PAYMENT.equals(name)) {
            processor.process(
                    "payment.order-awaiting-payment",
                    record,
                    AwaitingPaymentMessage.class,
                    event -> {
                        AwaitingPaymentMessage payload = event.payload();
                        prepare.execute(
                                new PreparePaymentCommand(
                                        payload.orderId(),
                                        payload.buyerId(),
                                        payload.amount(),
                                        payload.orderName()));
                    });
        }
    }

    /** 소비 쪽 페이로드. 취소 사유는 쓰지 않는다. */
    record CancelledMessage(UUID orderId) {}

    /** 소비 쪽 페이로드. */
    record AwaitingPaymentMessage(UUID orderId, UUID buyerId, long amount, String orderName) {}
}
