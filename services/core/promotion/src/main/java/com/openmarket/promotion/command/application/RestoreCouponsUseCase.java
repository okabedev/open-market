package com.openmarket.promotion.command.application;

import com.openmarket.promotion.command.domain.IssuedCoupon;
import com.openmarket.promotion.command.domain.IssuedCouponRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 전에 취소된 주문의 쿠폰을 되돌린다({@code OrderCancelled}, PRM-3). 만료 시각은 그대로라 지났으면 만료다. 묶인 쿠폰이 없으면(예약 실패, 쿠폰
 * 없는 주문) 아무것도 하지 않는다. 이벤트는 내지 않는다.
 */
@Service
public class RestoreCouponsUseCase {

    private final IssuedCouponRepository issuedCoupons;
    private final Clock clock;

    public RestoreCouponsUseCase(IssuedCouponRepository issuedCoupons, Clock clock) {
        this.issuedCoupons = issuedCoupons;
        this.clock = clock;
    }

    @Transactional
    public void execute(OrderCouponsCommand command) {
        Instant now = clock.instant();
        for (IssuedCoupon coupon :
                issuedCoupons.findForUpdateByOrderIdOrderById(command.orderId())) {
            if (coupon.restore(command.orderId(), now)) {
                issuedCoupons.save(coupon);
            }
        }
    }
}
