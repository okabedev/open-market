package com.openmarket.payment.ui.event;

import com.openmarket.common.messaging.consumer.IdempotentEventProcessor;
import com.openmarket.payment.command.application.RequestRefundCommand;
import com.openmarket.payment.command.application.RequestRefundUseCase;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * {@code fulfillment.claim} 토픽에서 {@code ClaimApproved}(환불 요청)를 받는다(docs/architecture/payment.md "환불
 * 요청"). 교환은 환불이 없어 처리 기록만 남기고 넘긴다. 같은 토픽의 다른 이벤트는 처리 기록 없이 넘긴다.
 */
@Component
class ClaimEventsListener {

    static final String CLAIM_APPROVED = "ClaimApproved";
    static final String EXCHANGE = "EXCHANGE";

    private final IdempotentEventProcessor processor;
    private final RequestRefundUseCase requestRefund;

    ClaimEventsListener(IdempotentEventProcessor processor, RequestRefundUseCase requestRefund) {
        this.processor = processor;
        this.requestRefund = requestRefund;
    }

    @KafkaListener(topics = "fulfillment.claim", groupId = "payment")
    void on(ConsumerRecord<String, String> record) {
        Header type = record.headers().lastHeader("type");
        String name = type == null ? "" : new String(type.value(), StandardCharsets.UTF_8);
        if (!CLAIM_APPROVED.equals(name)) {
            return;
        }
        processor.process(
                "payment.claim-approved",
                record,
                ApprovedMessage.class,
                event -> {
                    ApprovedMessage claim = event.payload();
                    if (EXCHANGE.equals(claim.type())) {
                        return;
                    }
                    requestRefund.execute(
                            new RequestRefundCommand(
                                    claim.claimId(),
                                    claim.orderId(),
                                    claim.sellerOrderId(),
                                    claim.refundAmount()));
                });
    }

    /** 소비 쪽 페이로드. 판매자, 구매자, 귀책은 쓰지 않는다. */
    record ApprovedMessage(
            UUID claimId, String type, UUID orderId, UUID sellerOrderId, long refundAmount) {}
}
