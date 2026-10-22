package com.openmarket.fulfillment.query.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 구매자 하위 주문의 배송 조회(FUL-2, FUL-3, docs/architecture/fulfillment.md "API"). 화면은 구매확정 버튼을 배송 완료이고 확정
 * 전일 때 보여 준다.
 *
 * @param status 마지막 배송의 상태
 * @param shipments 배송 목록. 만든 순서
 * @param confirmDueAt 자동 구매확정 기한. 배송 완료 뒤에만
 * @param confirmedAt 구매확정 시각. 확정했을 때만
 * @param confirmMode 구매확정 방식. 확정했을 때만
 * @param claims 클레임 목록. 신청 순서
 */
public record BuyerFulfillmentView(
        UUID sellerOrderId,
        UUID orderId,
        ShipmentStatus status,
        List<ShipmentView> shipments,
        @Nullable Instant confirmDueAt,
        @Nullable Instant confirmedAt,
        @Nullable ConfirmMode confirmMode,
        List<ClaimView> claims) {}
