package com.openmarket.order.infra.promotion;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;
import com.openmarket.order.command.application.CouponDiscountPort;
import feign.FeignException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@link CouponDiscountPort}의 OpenFeign 구현. promotion을 부를 수 없으면 503 {@code
 * ORDER_PROMOTION_UNAVAILABLE}이다. 타임아웃·재시도·서킷 브레이커는 catalog 조회와 같다(docs/architecture/order.md
 * "catalog 조회").
 */
@Component
class FeignCouponDiscountAdapter implements CouponDiscountPort {

    private static final Logger log = LoggerFactory.getLogger(FeignCouponDiscountAdapter.class);

    private final PromotionCouponDiscountClient client;

    FeignCouponDiscountAdapter(PromotionCouponDiscountClient client) {
        this.client = client;
    }

    @Override
    public CouponDiscount calculate(
            UUID buyerId, List<UUID> issuedCouponIds, List<SellerAmount> sellerOrders) {
        PromotionCouponDiscountClient.DiscountResponse response;
        try {
            response =
                    client.calculate(
                            new PromotionCouponDiscountClient.DiscountRequest(
                                    buyerId,
                                    issuedCouponIds,
                                    sellerOrders.stream()
                                            .map(
                                                    sellerOrder ->
                                                            new PromotionCouponDiscountClient
                                                                    .SellerOrderRequest(
                                                                    sellerOrder.sellerId(),
                                                                    sellerOrder.amount()))
                                            .toList()));
        } catch (RuntimeException e) {
            throw unavailable(e);
        }
        return new CouponDiscount(
                response.applicable(),
                response.sellerOrders().stream()
                        .map(
                                sellerOrder ->
                                        new SellerOrderCoupon(
                                                sellerOrder.sellerId(),
                                                sellerOrder.amount(),
                                                sellerOrder.sellerIssuedCouponId(),
                                                sellerOrder.sellerCouponDiscount(),
                                                sellerOrder.platformCouponDiscount(),
                                                sellerOrder.paymentAmount()))
                        .toList(),
                response.platformIssuedCouponId(),
                response.platformCouponDiscount(),
                response.paymentAmount(),
                response.unavailable().stream()
                        .map(
                                unavailable ->
                                        new Unavailable(
                                                unavailable.issuedCouponId(), unavailable.reason()))
                        .toList());
    }

    /** 서킷 브레이커가 감싼 예외를 503으로 바꾼다. 4xx는 order가 요청을 잘못 만든 것이라 그대로 오류(500)로 둔다. */
    private static RuntimeException unavailable(RuntimeException e) {
        Throwable root = e;
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof FeignException.FeignClientException clientError) {
                return new IllegalStateException("promotion이 요청을 거절했다: " + clientError.status(), e);
            }
            root = cause;
        }
        log.warn("promotion을 부를 수 없다: {}", root.toString());
        return new BusinessException(
                ErrorType.UNAVAILABLE,
                "ORDER_PROMOTION_UNAVAILABLE",
                "쿠폰 정보를 잠시 불러올 수 없습니다. 잠시 뒤 다시 시도해 주세요.",
                e);
    }
}
