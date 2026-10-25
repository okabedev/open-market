package com.openmarket.review.query.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 상품 리뷰 목록의 리뷰 한 건(공개). 작성자는 싣지 않는다.
 *
 * @param optionValues 산 옵션. 옵션이 없는 상품이면 빈 목록
 * @param fileIds 사진. 넣은 순서대로. 사진은 {@code /files/{fileId}/w300}, {@code /files/{fileId}/w800}으로 받는다
 * @param reply 판매자 답글. 없으면 {@code null}
 */
public record ProductReviewView(
        UUID reviewId,
        List<String> optionValues,
        int rating,
        String content,
        List<UUID> fileIds,
        @Nullable ReviewReplyView reply,
        Instant createdAt) {}
