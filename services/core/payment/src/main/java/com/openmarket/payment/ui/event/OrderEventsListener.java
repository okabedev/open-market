package com.openmarket.payment.ui.event;

import com.openmarket.common.messaging.consumer.IdempotentEventProcessor;
import com.openmarket.payment.command.application.PreparePaymentCommand;
import com.openmarket.payment.command.application.PreparePaymentUseCase;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * {@code order.order} 토픽에서 {@code OrderAwaitingPayment}를 받아 결제를 준비한다(docs/architecture/payment.md
 * "결제 준비"). 같은 토픽의 다른 이벤트는 처리 기록 없이 넘긴다.
 */
@Component
class OrderEventsListener {

    static final String ORDER_AWAITING_PAYMENT = "OrderAwaitingPayment";

    private final IdempotentEventProcessor processor;
    private final PreparePaymentUseCase prepare;

    OrderEventsListener(IdempotentEventProcessor processor, PreparePaymentUseCase prepare) {
        this.processor = processor;
        this.prepare = prepare;
    }

    @KafkaListener(topics = "order.order", groupId = "payment")
    void on(ConsumerRecord<String, String> record) {
        Header type = record.headers().lastHeader("type");
        String name = type == null ? "" : new String(type.value(), StandardCharsets.UTF_8);
        if (ORDER_AWAITING_PAYMENT.equals(name)) {
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

    /** 소비 쪽 페이로드. */
    record AwaitingPaymentMessage(UUID orderId, UUID buyerId, long amount, String orderName) {}
}
