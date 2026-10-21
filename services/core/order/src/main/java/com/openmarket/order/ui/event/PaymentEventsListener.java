package com.openmarket.order.ui.event;

import com.openmarket.common.messaging.consumer.IdempotentEventProcessor;
import com.openmarket.order.command.application.PayOrderCommand;
import com.openmarket.order.command.application.PayOrderUseCase;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * {@code payment.payment} 토픽에서 {@code PaymentApproved}(결제 완료)를 받는다(docs/architecture/order.md "결제
 * 결과"). 같은 토픽의 {@code PaymentFailed}는 처리 기록 없이 넘긴다. 주문은 결제 대기로 남아 다시 결제할 수 있다.
 */
@Component
class PaymentEventsListener {

    static final String PAYMENT_APPROVED = "PaymentApproved";

    private final IdempotentEventProcessor processor;
    private final PayOrderUseCase pay;

    PaymentEventsListener(IdempotentEventProcessor processor, PayOrderUseCase pay) {
        this.processor = processor;
        this.pay = pay;
    }

    @KafkaListener(topics = "payment.payment", groupId = "order")
    void on(ConsumerRecord<String, String> record) {
        Header type = record.headers().lastHeader("type");
        String name = type == null ? "" : new String(type.value(), StandardCharsets.UTF_8);
        if (PAYMENT_APPROVED.equals(name)) {
            processor.process(
                    "order.payment-approved",
                    record,
                    ApprovedMessage.class,
                    event ->
                            pay.execute(
                                    new PayOrderCommand(
                                            event.payload().orderId(),
                                            event.payload().amount(),
                                            event.payload().approvedAt())));
        }
    }

    /** 소비 쪽 페이로드. 결제 ID와 결제수단은 쓰지 않는다. */
    record ApprovedMessage(UUID orderId, long amount, Instant approvedAt) {}
}
