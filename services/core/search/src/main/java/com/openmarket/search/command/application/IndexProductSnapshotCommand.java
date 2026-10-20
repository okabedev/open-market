package com.openmarket.search.command.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 상품 스냅샷을 색인한다(docs/architecture/context-map.md "catalog.product").
 *
 * @param status catalog 상품 상태 이름
 * @param imageFileIds 순서대로. 첫 이미지를 썸네일로 쓴다
 */
public record IndexProductSnapshotCommand(
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
        @Nullable Instant publishedAt) {}
