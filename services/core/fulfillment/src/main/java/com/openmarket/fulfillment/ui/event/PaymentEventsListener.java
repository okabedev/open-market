package com.openmarket.fulfillment.ui.event;

import com.openmarket.common.messaging.consumer.IdempotentEventProcessor;
import com.openmarket.fulfillment.command.application.CompleteClaimCommand;
import com.openmarket.fulfillment.command.application.CompleteClaimUseCase;
import com.openmarket.fulfillment.command.application.RecordRefundFailureCommand;
import com.openmarket.fulfillment.command.application.RecordRefundFailureUseCase;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * {@code payment.payment} 토픽에서 {@code RefundCompleted}(클레임 완료), {@code RefundFailed}(실패 코드 기록)를
 * 받는다(docs/architecture/fulfillment.md "환불 결과 반영"). 같은 토픽의 다른 이벤트는 처리 기록 없이 넘긴다.
 */
@Component
class PaymentEventsListener {

    static final String REFUND_COMPLETED = "RefundCompleted";
    static final String REFUND_FAILED = "RefundFailed";

    private final IdempotentEventProcessor processor;
    private final CompleteClaimUseCase complete;
    private final RecordRefundFailureUseCase recordFailure;

    PaymentEventsListener(
            IdempotentEventProcessor processor,
            CompleteClaimUseCase complete,
            RecordRefundFailureUseCase recordFailure) {
        this.processor = processor;
        this.complete = complete;
        this.recordFailure = recordFailure;
    }

    @KafkaListener(topics = "payment.payment", groupId = "fulfillment")
    void on(ConsumerRecord<String, String> record) {
        Header type = record.headers().lastHeader("type");
        String name = type == null ? "" : new String(type.value(), StandardCharsets.UTF_8);
        switch (name) {
            case REFUND_COMPLETED ->
                    processor.process(
                            "fulfillment.refund-completed",
                            record,
                            RefundMessage.class,
                            event ->
                                    complete.execute(
                                            new CompleteClaimCommand(event.payload().claimId())));
            case REFUND_FAILED ->
                    processor.process(
                            "fulfillment.refund-failed",
                            record,
                            RefundFailedMessage.class,
                            event ->
                                    recordFailure.execute(
                                            new RecordRefundFailureCommand(
                                                    event.payload().claimId(),
                                                    event.payload().code())));
            default -> {}
        }
    }

    /** {@code RefundCompleted}. 클레임 ID만 쓴다. 금액은 클레임에 있다. */
    record RefundMessage(UUID claimId) {}

    /** {@code RefundFailed}. */
    record RefundFailedMessage(UUID claimId, String code) {}
}
