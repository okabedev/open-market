package com.openmarket.order.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 주문에 쓸 수 없는 쿠폰이 있다. 422다. 쿠폰마다 이유를 응답에 싣는다(docs/architecture/order.md "쿠폰 적용"). */
public class CouponNotApplicableException extends BusinessException {

    private final List<Coupon> coupons;

    public CouponNotApplicableException(List<Coupon> coupons) {
        super(ErrorType.UNPROCESSABLE, "ORDER_COUPON_NOT_APPLICABLE", "쓸 수 없는 쿠폰이 있습니다.");
        this.coupons = List.copyOf(coupons);
    }

    @Override
    public Map<String, Object> getProperties() {
        return Map.of("coupons", coupons);
    }

    /**
     * 쓸 수 없는 쿠폰.
     *
     * @param reason promotion의 이유 코드(docs/architecture/promotion.md "할인 계산")
     */
    public record Coupon(UUID issuedCouponId, String reason) {}
}
