package com.openmarket.order.command.domain;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 하위 주문 하나의 쿠폰 할인(docs/architecture/promotion.md "할인 계산"). promotion이 계산한 값을 주문에 스냅샷으로 남긴다.
 *
 * @param sellerIssuedCouponId 이 하위 주문에 쓴 판매자 쿠폰. 없으면 비어 있다
 * @param sellerCouponDiscount 판매자 쿠폰 할인(원)
 * @param platformCouponDiscount 플랫폼 쿠폰 할인 중 이 하위 주문의 몫(원)
 */
public record SellerOrderDiscount(
        UUID sellerId,
        @Nullable UUID sellerIssuedCouponId,
        long sellerCouponDiscount,
        long platformCouponDiscount) {}
