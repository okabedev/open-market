package com.openmarket.order.ui.event;

import com.openmarket.common.messaging.consumer.IdempotentEventProcessor;
import com.openmarket.order.command.application.ApplyClaimCommand;
import com.openmarket.order.command.application.ApplyClaimUseCase;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * {@code fulfillment.claim} 토픽의 클레임 이벤트로 하위 주문 클레임을 바꾼다(docs/architecture/order.md "하위 주문 클레임"). 그
 * 밖의 이벤트는 처리 기록 없이 넘긴다.
 */
@Component
class ClaimEventsListener {

    /** 이벤트 이름 → 클레임 상태. 리스너 이름은 {@code order.claim-<상태>}다(예: {@code order.claim-approved}). */
    static final Map<String, ApplyClaimCommand.Status> STATUSES =
            Map.of(
                    "ClaimRequested", ApplyClaimCommand.Status.REQUESTED,
                    "ClaimApproved", ApplyClaimCommand.Status.APPROVED,
                    "ClaimRejected", ApplyClaimCommand.Status.REJECTED,
                    "ClaimCompleted", ApplyClaimCommand.Status.COMPLETED);

    private final IdempotentEventProcessor processor;
    private final ApplyClaimUseCase applyClaim;

    ClaimEventsListener(IdempotentEventProcessor processor, ApplyClaimUseCase applyClaim) {
        this.processor = processor;
        this.applyClaim = applyClaim;
    }

    @KafkaListener(topics = "fulfillment.claim", groupId = "order")
    void on(ConsumerRecord<String, String> record) {
        Header type = record.headers().lastHeader("type");
        String name = type == null ? "" : new String(type.value(), StandardCharsets.UTF_8);
        ApplyClaimCommand.Status status = STATUSES.get(name);
        if (status == null) {
            return;
        }
        processor.process(
                "order.claim-" + status.name().toLowerCase(Locale.ROOT),
                record,
                ClaimMessage.class,
                event ->
                        applyClaim.execute(
                                new ApplyClaimCommand(
                                        event.payload().orderId(),
                                        event.payload().sellerOrderId(),
                                        event.payload().claimId(),
                                        ApplyClaimCommand.Type.valueOf(event.payload().type()),
                                        status)));
    }

    /** 소비 쪽 페이로드. 세 이벤트 모두 이 필드를 갖는다. */
    record ClaimMessage(UUID claimId, String type, UUID orderId, UUID sellerOrderId) {}
}
