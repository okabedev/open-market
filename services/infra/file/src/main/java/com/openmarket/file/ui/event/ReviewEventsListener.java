package com.openmarket.file.ui.event;

import com.openmarket.common.messaging.consumer.IdempotentEventProcessor;
import com.openmarket.file.command.application.AttachReviewImagesCommand;
import com.openmarket.file.command.application.AttachReviewImagesUseCase;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * {@code review.review} 토픽에서 {@code ReviewCreated}를 받아 리뷰 사진을 붙인다(docs/architecture/file.md "리뷰
 * 사진"). 같은 토픽의 다른 이벤트는 처리 기록 없이 넘긴다.
 */
@Component
class ReviewEventsListener {

    static final String REVIEW_CREATED = "ReviewCreated";

    private final IdempotentEventProcessor processor;
    private final AttachReviewImagesUseCase attachReviewImages;

    ReviewEventsListener(
            IdempotentEventProcessor processor, AttachReviewImagesUseCase attachReviewImages) {
        this.processor = processor;
        this.attachReviewImages = attachReviewImages;
    }

    @KafkaListener(topics = "review.review", groupId = "file")
    void on(ConsumerRecord<String, String> record) {
        Header type = record.headers().lastHeader("type");
        String name = type == null ? "" : new String(type.value(), StandardCharsets.UTF_8);
        if (!REVIEW_CREATED.equals(name)) {
            return;
        }
        processor.process(
                "file.review-images",
                record,
                ReviewCreatedMessage.class,
                event ->
                        attachReviewImages.execute(
                                new AttachReviewImagesCommand(
                                        event.payload().reviewId(),
                                        event.payload().buyerId(),
                                        event.payload().sellerId(),
                                        event.payload().fileIds())));
    }

    /** 소비 쪽 페이로드. 첨부에 쓰는 필드만 읽는다. */
    record ReviewCreatedMessage(UUID reviewId, UUID buyerId, UUID sellerId, List<UUID> fileIds) {}
}
