package com.openmarket.promotion.ui.web;

import com.openmarket.common.web.idempotency.Idempotent;
import com.openmarket.promotion.command.application.CreateCouponUseCase;
import com.openmarket.promotion.command.application.StopCouponCommand;
import com.openmarket.promotion.command.application.StopCouponUseCase;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 판매자 쿠폰(PRM-1). 주체는 토큰의 {@code sub}(= sellerId)다. 판매자 쿠폰은 그 판매자의 모든 상품에 쓴다. */
@RestController
@RequestMapping("/v1/seller/coupons")
public class SellerCouponCommandController {

    private final CreateCouponUseCase create;
    private final StopCouponUseCase stop;

    public SellerCouponCommandController(CreateCouponUseCase create, StopCouponUseCase stop) {
        this.create = create;
        this.stop = stop;
    }

    /**
     * 판매자 쿠폰을 만든다. {@code Idempotency-Key}가 필요하다. 입력 규칙 위반은 400 {@code PROMOTION_INVALID_INPUT}.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Idempotent
    public CreateCouponResponse create(
            Principal principal, @Valid @RequestBody CreateCouponRequest request) {
        UUID sellerId = UUID.fromString(principal.getName());
        return new CreateCouponResponse(create.execute(request.toCommand(sellerId, sellerId)));
    }

    /** 자기 쿠폰의 발급을 멈춘다. 이미 멈췄으면 204. 다른 판매자의 쿠폰이면 404 {@code PROMOTION_COUPON_NOT_FOUND}. */
    @PostMapping("/{couponId}/stop")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void stop(Principal principal, @PathVariable UUID couponId) {
        UUID sellerId = UUID.fromString(principal.getName());
        stop.execute(new StopCouponCommand(sellerId, sellerId, couponId));
    }
}
