package com.openmarket.review.query.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 리뷰 한 건(내 리뷰, 판매자 리뷰, 관리자 리뷰). 숨긴 리뷰도 상태와 함께 보인다.
 *
 * @param optionValues 산 옵션. 옵션이 없는 상품이면 빈 목록
 * @param fileIds 사진. 넣은 순서대로. 사진은 {@code /files/{fileId}/w300}, {@code /files/{fileId}/w800}으로 받는다
 * @param reply 판매자 답글. 없으면 {@code null}
 */
public record ReviewView(
        UUID reviewId,
        UUID orderLineId,
        UUID productId,
        String productName,
        List<String> optionValues,
        UUID sellerId,
        UUID buyerId,
        int rating,
        String content,
        List<UUID> fileIds,
        ReviewStatus status,
        @Nullable ReviewReplyView reply,
        Instant createdAt) {}
