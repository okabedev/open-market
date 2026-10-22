package com.openmarket.catalog.ui.event;

import com.openmarket.catalog.command.application.RestockClaimCommand;
import com.openmarket.catalog.command.application.RestockClaimUseCase;
import com.openmarket.common.messaging.consumer.IdempotentEventProcessor;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * {@code fulfillment.claim} 토픽에서 {@code ClaimCompleted}(재입고)를 받는다(docs/architecture/catalog.md "클레임
 * 재입고"). 같은 토픽의 다른 이벤트는 처리 기록 없이 넘긴다.
 */
@Component
class ClaimEventsListener {

    static final String CLAIM_COMPLETED = "ClaimCompleted";

    private final IdempotentEventProcessor processor;
    private final RestockClaimUseCase restock;

    ClaimEventsListener(IdempotentEventProcessor processor, RestockClaimUseCase restock) {
        this.processor = processor;
        this.restock = restock;
    }

    @KafkaListener(topics = "fulfillment.claim", groupId = "catalog")
    void on(ConsumerRecord<String, String> record) {
        Header type = record.headers().lastHeader("type");
        String name = type == null ? "" : new String(type.value(), StandardCharsets.UTF_8);
        if (!CLAIM_COMPLETED.equals(name)) {
            return;
        }
        processor.process(
                "catalog.claim-completed",
                record,
                CompletedMessage.class,
                event ->
                        restock.execute(
                                new RestockClaimCommand(
                                        event.payload().claimId(),
                                        event.payload().restockLines().stream()
                                                .map(
                                                        line ->
                                                                new RestockClaimCommand.Line(
                                                                        line.skuId(),
                                                                        line.quantity()))
                                                .toList())));
    }

    /** 소비 쪽 페이로드. 재입고 항목만 쓴다. */
    record CompletedMessage(UUID claimId, List<Line> restockLines) {

        record Line(UUID skuId, int quantity) {}
    }
}
