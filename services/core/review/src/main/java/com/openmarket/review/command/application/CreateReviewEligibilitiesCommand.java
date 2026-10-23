package com.openmarket.review.command.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 구매확정한 하위 주문의 항목마다 리뷰 작성 자격을 만든다({@code PurchaseConfirmed}). */
public record CreateReviewEligibilitiesCommand(
        UUID orderId,
        UUID sellerOrderId,
        UUID sellerId,
        UUID buyerId,
        Instant confirmedAt,
        List<Line> lines) {

    /**
     * 주문 항목 하나.
     *
     * @param optionValues 옵션이 없는 상품이면 빈 목록
     */
    public record Line(
            UUID orderLineId, UUID productId, String productName, List<String> optionValues) {}
}
