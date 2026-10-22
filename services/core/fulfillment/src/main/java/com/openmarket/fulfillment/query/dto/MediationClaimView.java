package com.openmarket.fulfillment.query.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * 관리자 중재 목록의 한 줄(docs/architecture/fulfillment.md "중재").
 *
 * @param amount 하위 주문 금액(원). 중재 승인의 환불 금액 기준이다
 * @param mediationRequestedAt 중재 요청 시각. 목록은 이 순서다
 * @param claim 클레임(사유, 설명, 사진, 판매자 거부 사유 등)
 */
public record MediationClaimView(
        UUID orderId,
        UUID sellerOrderId,
        UUID sellerId,
        UUID buyerId,
        long amount,
        Instant mediationRequestedAt,
        ClaimView claim) {}
