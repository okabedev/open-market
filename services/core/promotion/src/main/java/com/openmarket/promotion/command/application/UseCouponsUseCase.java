package com.openmarket.promotion.command.application;

import com.openmarket.promotion.command.domain.IssuedCoupon;
import com.openmarket.promotion.command.domain.IssuedCouponRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 결제된 주문에 묶인 쿠폰을 사용 확정한다({@code OrderPaid}). 묶인 쿠폰이 없거나 이미 썼으면 아무것도 하지 않는다. 이벤트는 내지 않는다. */
@Service
public class UseCouponsUseCase {

    private final IssuedCouponRepository issuedCoupons;
    private final Clock clock;

    public UseCouponsUseCase(IssuedCouponRepository issuedCoupons, Clock clock) {
        this.issuedCoupons = issuedCoupons;
        this.clock = clock;
    }

    @Transactional
    public void execute(OrderCouponsCommand command) {
        Instant now = clock.instant();
        for (IssuedCoupon coupon :
                issuedCoupons.findForUpdateByOrderIdOrderById(command.orderId())) {
            if (coupon.use(command.orderId(), now)) {
                issuedCoupons.save(coupon);
            }
        }
    }
}
