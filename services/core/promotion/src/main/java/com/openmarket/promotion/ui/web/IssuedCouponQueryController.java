package com.openmarket.promotion.ui.web;

import com.openmarket.promotion.query.application.IssuedCouponQueryService;
import com.openmarket.promotion.query.dto.CursorPageView;
import com.openmarket.promotion.query.dto.IssuedCouponView;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.security.Principal;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 구매자 내 쿠폰. 주체는 토큰의 {@code sub}(= 회원 ID)다. */
@RestController
@RequestMapping("/v1/issued-coupons")
public class IssuedCouponQueryController {

    private final IssuedCouponQueryService issuedCoupons;

    public IssuedCouponQueryController(IssuedCouponQueryService issuedCoupons) {
        this.issuedCoupons = issuedCoupons;
    }

    /**
     * 내가 받은 쿠폰을 최근 발급 순으로 준다. 항목의 {@code state}는 쓸 수 있음·주문에 묶임·사용·만료다. {@code usable=true}면 지금 쓸 수
     * 있는 쿠폰만 준다(주문서). 커서가 틀리면 400 {@code PROMOTION_INVALID_CURSOR}.
     */
    @GetMapping
    public CursorPageView<IssuedCouponView> mine(
            Principal principal,
            @RequestParam(defaultValue = "false") boolean usable,
            @RequestParam(required = false) @Nullable String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return issuedCoupons.mine(UUID.fromString(principal.getName()), usable, cursor, size);
    }
}
