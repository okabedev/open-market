package com.openmarket.promotion.ui.web;

import com.openmarket.promotion.query.application.CouponQueryService;
import com.openmarket.promotion.query.dto.CouponView;
import com.openmarket.promotion.query.dto.IssuableCouponsView;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 공개 쿠폰 조회. 토큰 없이 부른다(쿠폰 받기 화면, 상품 상세의 판매자 쿠폰). */
@RestController
@RequestMapping("/v1/coupons")
public class CouponQueryController {

    private final CouponQueryService coupons;

    public CouponQueryController(CouponQueryService coupons) {
        this.coupons = coupons;
    }

    /**
     * 지금 내려받을 수 있는 쿠폰(발급 중이고 발급 기간 안). {@code sellerId}가 없으면 플랫폼 쿠폰, 있으면 그 판매자 쿠폰. 발급 기간이 먼저 끝나는
     * 순으로 최대 100개.
     */
    @GetMapping
    public IssuableCouponsView issuable(@RequestParam(required = false) @Nullable UUID sellerId) {
        return coupons.issuable(sellerId);
    }

    /** 쿠폰 한 건. 상태와 상관없이 보인다. 없으면 404 {@code PROMOTION_COUPON_NOT_FOUND}. */
    @GetMapping("/{couponId}")
    public CouponView coupon(@PathVariable UUID couponId) {
        return coupons.coupon(couponId);
    }
}
