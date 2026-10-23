package com.openmarket.order.infra.promotion;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * promotion 내부 API(docs/architecture/promotion.md "할인 계산 API(내부)"). Eureka로 찾고, 서비스 간 토큰과 서킷 브레이커가
 * 붙는다(config-repo order.yml). {@code openmarket.order.promotion-url}이 있으면 그 주소로 바로 부른다(테스트).
 */
@FeignClient(
        name = "promotion",
        contextId = "promotionCouponDiscount",
        url = "${openmarket.order.promotion-url:}")
interface PromotionCouponDiscountClient {

    @PostMapping("/internal/v1/coupon-discounts")
    DiscountResponse calculate(@RequestBody DiscountRequest request);

    record DiscountRequest(
            UUID buyerId, List<UUID> issuedCouponIds, List<SellerOrderRequest> sellerOrders) {}

    record SellerOrderRequest(UUID sellerId, long amount) {}

    record DiscountResponse(
            boolean applicable,
            List<SellerOrderResponse> sellerOrders,
            @Nullable UUID platformIssuedCouponId,
            long platformCouponDiscount,
            long paymentAmount,
            List<UnavailableResponse> unavailable) {}

    record SellerOrderResponse(
            UUID sellerId,
            long amount,
            @Nullable UUID sellerIssuedCouponId,
            long sellerCouponDiscount,
            long platformCouponDiscount,
            long paymentAmount) {}

    record UnavailableResponse(UUID issuedCouponId, String reason) {}
}
