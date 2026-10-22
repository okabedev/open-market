package com.openmarket.file.ui.event;

import com.openmarket.common.messaging.consumer.IdempotentEventProcessor;
import com.openmarket.file.command.application.AttachClaimImagesCommand;
import com.openmarket.file.command.application.AttachClaimImagesUseCase;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * {@code fulfillment.claim} 토픽에서 {@code ClaimRequested}를 받아 클레임 사진을 붙인다(docs/architecture/file.md
 * "클레임 사진"). 같은 토픽의 다른 이벤트는 처리 기록 없이 넘긴다.
 */
@Component
class ClaimEventsListener {

    static final String CLAIM_REQUESTED = "ClaimRequested";

    private final IdempotentEventProcessor processor;
    private final AttachClaimImagesUseCase attachClaimImages;

    ClaimEventsListener(
            IdempotentEventProcessor processor, AttachClaimImagesUseCase attachClaimImages) {
        this.processor = processor;
        this.attachClaimImages = attachClaimImages;
    }

    @KafkaListener(topics = "fulfillment.claim", groupId = "file")
    void on(ConsumerRecord<String, String> record) {
        Header type = record.headers().lastHeader("type");
        String name = type == null ? "" : new String(type.value(), StandardCharsets.UTF_8);
        if (!CLAIM_REQUESTED.equals(name)) {
            return;
        }
        processor.process(
                "file.claim-images",
                record,
                ClaimRequestedMessage.class,
                event ->
                        attachClaimImages.execute(
                                new AttachClaimImagesCommand(
                                        event.payload().claimId(),
                                        event.payload().buyerId(),
                                        event.payload().sellerId(),
                                        event.payload().fileIds())));
    }

    /** 소비 쪽 페이로드. 첨부에 쓰는 필드만 읽는다. */
    record ClaimRequestedMessage(UUID claimId, UUID buyerId, UUID sellerId, List<UUID> fileIds) {}
}
