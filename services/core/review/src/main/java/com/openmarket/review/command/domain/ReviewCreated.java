package com.openmarket.review.command.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 리뷰를 썼다(docs/architecture/context-map.md "review.review"). file이 사진을 붙이고 notification이 판매자에게 알린다.
 * 본문은 싣지 않는다.
 */
public record ReviewCreated(
        UUID reviewId,
        UUID orderLineId,
        UUID productId,
        String productName,
        UUID sellerId,
        UUID buyerId,
        int rating,
        List<UUID> fileIds,
        Instant createdAt) {

    public static final String TYPE = "ReviewCreated";

    public static ReviewCreated of(Review review) {
        return new ReviewCreated(
                review.id(),
                review.orderLineId(),
                review.productId(),
                review.productName(),
                review.sellerId(),
                review.buyerId(),
                review.rating(),
                review.fileIds(),
                review.createdAt());
    }
}
