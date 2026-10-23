package com.openmarket.promotion.ui.web;

import com.openmarket.promotion.query.application.CouponQueryService;
import com.openmarket.promotion.query.dto.CouponStatus;
import com.openmarket.promotion.query.dto.CouponView;
import com.openmarket.promotion.query.dto.PageView;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.security.Principal;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 판매자 쿠폰 목록. 쪽 번호 목록이다. */
@RestController
@RequestMapping("/v1/seller/coupons")
public class SellerCouponQueryController {

    private final CouponQueryService coupons;

    public SellerCouponQueryController(CouponQueryService coupons) {
        this.coupons = coupons;
    }

    /** 자기 쿠폰. 상태로 거른다. 주지 않으면 전체. 최근 생성 순. */
    @GetMapping
    public PageView<CouponView> list(
            Principal principal,
            @RequestParam(required = false) @Nullable CouponStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return coupons.sellerCoupons(UUID.fromString(principal.getName()), status, page, size);
    }
}
