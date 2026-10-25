package com.openmarket.notification.ui.event;

import com.openmarket.common.messaging.consumer.IdempotentEventProcessor;
import com.openmarket.notification.command.application.NotifyCommand;
import com.openmarket.notification.command.application.NotifyUseCase;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * {@code review.review} 토픽에서 {@code ReviewCreated}만 받아 판매자에게 새 리뷰 인앱 알림을 만든다. 문구에는 상품명과 별점만 넣고 리뷰
 * 본문은 넣지 않는다. 그 밖의 이벤트는 처리 기록 없이 넘긴다.
 */
@Component
class ReviewEventsListener {

    static final String LISTENER = "notification.review-created";

    private final IdempotentEventProcessor processor;
    private final NotifyUseCase notify;

    ReviewEventsListener(IdempotentEventProcessor processor, NotifyUseCase notify) {
        this.processor = processor;
        this.notify = notify;
    }

    @KafkaListener(topics = "review.review", groupId = "notification")
    void on(ConsumerRecord<String, String> record) {
        if (!"ReviewCreated".equals(type(record))) {
            return;
        }
        processor.process(
                LISTENER,
                record,
                ReviewCreatedMessage.class,
                event ->
                        notify.execute(
                                new NotifyCommand(
                                        event.id(),
                                        List.of(
                                                new NotifyCommand.Notice(
                                                        NotifyCommand.Kind.REVIEW_CREATED,
                                                        NotifyCommand.Audience.SELLER,
                                                        event.payload().sellerId(),
                                                        Map.of(
                                                                "productName",
                                                                event.payload().productName(),
                                                                "rating",
                                                                event.payload().rating() + ""),
                                                        NotifyCommand.Target.REVIEW,
                                                        event.payload().reviewId())))));
    }

    /** 소비 쪽 페이로드. 알림에 쓰는 필드만 둔다. */
    record ReviewCreatedMessage(UUID reviewId, String productName, UUID sellerId, int rating) {}

    private static String type(ConsumerRecord<String, String> record) {
        Header type = record.headers().lastHeader("type");
        return type == null ? "" : new String(type.value(), StandardCharsets.UTF_8);
    }
}
