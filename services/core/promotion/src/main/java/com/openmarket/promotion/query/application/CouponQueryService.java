package com.openmarket.promotion.query.application;

import com.openmarket.common.core.error.NotFoundException;
import com.openmarket.promotion.query.dao.CouponQueryDao;
import com.openmarket.promotion.query.dto.CouponIssuer;
import com.openmarket.promotion.query.dto.CouponStatus;
import com.openmarket.promotion.query.dto.CouponView;
import com.openmarket.promotion.query.dto.IssuableCouponsView;
import com.openmarket.promotion.query.dto.PageView;
import java.time.Clock;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 쿠폰 정책 조회(공개, 판매자, 관리자). */
@Service
@Transactional(readOnly = true)
public class CouponQueryService {

    private final CouponQueryDao coupons;
    private final Clock clock;

    public CouponQueryService(CouponQueryDao coupons, Clock clock) {
        this.coupons = coupons;
        this.clock = clock;
    }

    /** 쿠폰 한 건. 상태와 상관없이 보인다. 없으면 404 {@code PROMOTION_COUPON_NOT_FOUND}. */
    public CouponView coupon(UUID couponId) {
        return coupons.find(couponId)
                .orElseThrow(
                        () ->
                                new NotFoundException(
                                        "PROMOTION_COUPON_NOT_FOUND", "쿠폰을 찾을 수 없습니다."));
    }

    /** 지금 내려받을 수 있는 쿠폰. {@code sellerId}가 null이면 플랫폼 쿠폰. */
    public IssuableCouponsView issuable(@Nullable UUID sellerId) {
        return new IssuableCouponsView(coupons.findIssuable(sellerId, clock.instant()));
    }

    public PageView<CouponView> sellerCoupons(
            UUID sellerId, @Nullable CouponStatus status, int page, int size) {
        return coupons.findPage(CouponIssuer.SELLER, sellerId, status, page, size);
    }

    public PageView<CouponView> adminCoupons(
            @Nullable CouponIssuer issuer,
            @Nullable UUID sellerId,
            @Nullable CouponStatus status,
            int page,
            int size) {
        return coupons.findPage(issuer, sellerId, status, page, size);
    }
}
