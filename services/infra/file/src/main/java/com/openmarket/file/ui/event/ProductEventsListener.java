package com.openmarket.file.ui.event;

import com.openmarket.common.messaging.consumer.IdempotentEventProcessor;
import com.openmarket.file.command.application.SyncProductImagesCommand;
import com.openmarket.file.command.application.SyncProductImagesUseCase;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * {@code catalog.product} 토픽에서 상품 스냅샷 이벤트({@code ProductRegistered}, {@code ProductPublished},
 * {@code ProductChanged})를 받아 이미지 첨부를 맞춘다. 같은 토픽의 다른 이벤트(판매 상태)는 처리 기록 없이 넘긴다. 키가 productId라 한 상품의
 * 이벤트는 순서대로 온다.
 */
@Component
class ProductEventsListener {

    static final Set<String> SNAPSHOT_TYPES =
            Set.of("ProductRegistered", "ProductPublished", "ProductChanged");

    private final IdempotentEventProcessor processor;
    private final SyncProductImagesUseCase syncProductImages;

    ProductEventsListener(
            IdempotentEventProcessor processor, SyncProductImagesUseCase syncProductImages) {
        this.processor = processor;
        this.syncProductImages = syncProductImages;
    }

    @KafkaListener(topics = "catalog.product", groupId = "file")
    void on(ConsumerRecord<String, String> record) {
        Header type = record.headers().lastHeader("type");
        String name = type == null ? "" : new String(type.value(), StandardCharsets.UTF_8);
        if (!SNAPSHOT_TYPES.contains(name)) {
            return;
        }
        processor.process(
                "file.product-images",
                record,
                ProductSnapshotMessage.class,
                event ->
                        syncProductImages.execute(
                                new SyncProductImagesCommand(
                                        event.payload().productId(),
                                        event.payload().revision(),
                                        event.payload().sellerId(),
                                        event.payload().imageFileIds())));
    }

    /** 소비 쪽 페이로드. 상품 스냅샷 중 첨부에 쓰는 필드만 읽는다(docs/architecture/messaging.md "스키마 규칙"). */
    record ProductSnapshotMessage(
            UUID productId, long revision, UUID sellerId, List<UUID> imageFileIds) {}
}
