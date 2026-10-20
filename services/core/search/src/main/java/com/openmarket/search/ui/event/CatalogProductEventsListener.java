package com.openmarket.search.ui.event;

import com.openmarket.common.messaging.consumer.IdempotentEventProcessor;
import com.openmarket.search.command.application.ChangeProductStatusCommand;
import com.openmarket.search.command.application.ChangeProductStatusUseCase;
import com.openmarket.search.command.application.IndexProductSnapshotCommand;
import com.openmarket.search.command.application.IndexProductSnapshotUseCase;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.jspecify.annotations.Nullable;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * {@code catalog.product} 토픽의 상품 이벤트를 검색 상품에 반영한다(docs/architecture/search.md "색인"). 키가 productId라
 * 한 상품의 이벤트는 순서대로 온다. 재처리로 오래된 이벤트가 와도 revision으로 거른다.
 */
@Component
class CatalogProductEventsListener {

    private final IdempotentEventProcessor processor;
    private final IndexProductSnapshotUseCase indexSnapshot;
    private final ChangeProductStatusUseCase changeStatus;

    CatalogProductEventsListener(
            IdempotentEventProcessor processor,
            IndexProductSnapshotUseCase indexSnapshot,
            ChangeProductStatusUseCase changeStatus) {
        this.processor = processor;
        this.indexSnapshot = indexSnapshot;
        this.changeStatus = changeStatus;
    }

    @KafkaListener(topics = "catalog.product", groupId = "search")
    void on(ConsumerRecord<String, String> record) {
        Header type = record.headers().lastHeader("type");
        String name = type == null ? "" : new String(type.value(), StandardCharsets.UTF_8);
        switch (name) {
            case "ProductRegistered", "ProductPublished", "ProductChanged" ->
                    processor.process(
                            "search.product-snapshot",
                            record,
                            ProductSnapshotMessage.class,
                            event -> indexSnapshot.execute(event.payload().toCommand()));
            case "ProductStopped" -> status(record, ChangeProductStatusCommand.Status.STOPPED);
            case "ProductResumed" -> status(record, ChangeProductStatusCommand.Status.ON_SALE);
            case "ProductBanned" -> status(record, ChangeProductStatusCommand.Status.BANNED);
            default -> {}
        }
    }

    private void status(
            ConsumerRecord<String, String> record, ChangeProductStatusCommand.Status status) {
        processor.process(
                "search.product-status",
                record,
                ProductStatusMessage.class,
                event ->
                        changeStatus.execute(
                                new ChangeProductStatusCommand(
                                        event.payload().productId(),
                                        event.payload().revision(),
                                        status)));
    }

    /** 상품 스냅샷 중 검색에 쓰는 필드(docs/architecture/messaging.md "스키마 규칙"). */
    record ProductSnapshotMessage(
            UUID productId,
            long revision,
            String status,
            UUID sellerId,
            String storeName,
            UUID categoryId,
            List<UUID> categoryPath,
            String name,
            String description,
            List<UUID> imageFileIds,
            long minPrice,
            boolean inStock,
            long availabilityRevision,
            @Nullable Instant publishedAt) {

        IndexProductSnapshotCommand toCommand() {
            return new IndexProductSnapshotCommand(
                    productId,
                    revision,
                    status,
                    sellerId,
                    storeName,
                    categoryId,
                    categoryPath,
                    name,
                    description,
                    imageFileIds,
                    minPrice,
                    inStock,
                    availabilityRevision,
                    publishedAt);
        }
    }

    /** {@code ProductStopped}, {@code ProductResumed}, {@code ProductBanned}. 사유는 쓰지 않는다. */
    record ProductStatusMessage(UUID productId, long revision) {}
}
