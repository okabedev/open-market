package com.openmarket.fulfillment.query.dto;

import java.util.List;
import java.util.UUID;

/**
 * 판매자 하위 주문의 배송 진행(docs/architecture/fulfillment.md "API"). 항목과 배송지는 order의 판매자 하위 주문 상세에 있다.
 *
 * @param status 마지막 배송의 상태
 * @param shipments 배송 목록. 만든 순서이고 교환 재발송이 있으면 둘 이상이다
 * @param claims 클레임 목록. 신청 순서
 */
public record SellerFulfillmentView(
        UUID sellerOrderId,
        UUID orderId,
        ShipmentStatus status,
        List<ShipmentView> shipments,
        List<ClaimView> claims) {}
