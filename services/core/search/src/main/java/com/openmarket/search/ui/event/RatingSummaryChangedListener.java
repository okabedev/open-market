package com.openmarket.search.ui.event;

import com.openmarket.common.messaging.consumer.IdempotentEventProcessor;
import com.openmarket.search.command.application.ChangeProductRatingCommand;
import com.openmarket.search.command.application.ChangeProductRatingUseCase;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.jspecify.annotations.Nullable;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * {@code review.rating-summary} 토픽의 {@code RatingSummaryChanged}를 검색 상품에
 * 반영한다(docs/architecture/search.md "별점"). 리비전으로 오래된 값을 거르므로 다시 받거나 순서가 섞여도 결과가 같다.
 */
@Component
class RatingSummaryChangedListener {

    static final String TYPE = "RatingSummaryChanged";

    private final IdempotentEventProcessor processor;
    private final ChangeProductRatingUseCase changeRating;

    RatingSummaryChangedListener(
            IdempotentEventProcessor processor, ChangeProductRatingUseCase changeRating) {
        this.processor = processor;
        this.changeRating = changeRating;
    }

    @KafkaListener(topics = "review.rating-summary", groupId = "search")
    void on(ConsumerRecord<String, String> record) {
        Header type = record.headers().lastHeader("type");
        String name = type == null ? "" : new String(type.value(), StandardCharsets.UTF_8);
        if (!TYPE.equals(name)) {
            return;
        }
        processor.process(
                "search.rating-summary-changed",
                record,
                RatingSummaryChangedMessage.class,
                event ->
                        changeRating.execute(
                                new ChangeProductRatingCommand(
                                        event.payload().productId(),
                                        event.payload().ratingCount(),
                                        event.payload().ratingAverage(),
                                        event.payload().revision())));
    }

    /** 소비 쪽 페이로드(docs/architecture/messaging.md "스키마 규칙"). 합(ratingSum)은 읽지 않는다. */
    record RatingSummaryChangedMessage(
            UUID productId, long ratingCount, @Nullable BigDecimal ratingAverage, long revision) {}
}
