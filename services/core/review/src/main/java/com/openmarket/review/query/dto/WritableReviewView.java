package com.openmarket.review.query.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 구매자가 아직 리뷰를 쓰지 않은 구매확정 주문 항목(작성 자격).
 *
 * @param optionValues 산 옵션. 옵션이 없는 상품이면 빈 목록
 */
public record WritableReviewView(
        UUID orderLineId,
        UUID orderId,
        UUID productId,
        String productName,
        List<String> optionValues,
        Instant confirmedAt) {}
