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

/** 관리자 쿠폰 관리(PRM-1, ADM-3). 주체는 토큰의 {@code sub}(= 관리자 ID)이고 감사 로그에 남는다. */
@RestController
@RequestMapping("/v1/admin/coupons")
public class AdminCouponCommandController {

    private final CreateCouponUseCase create;
    private final StopCouponUseCase stop;

    public AdminCouponCommandController(CreateCouponUseCase create, StopCouponUseCase stop) {
        this.create = create;
        this.stop = stop;
    }

    /**
     * 플랫폼 쿠폰을 만든다. {@code Idempotency-Key}가 필요하다. 입력 규칙 위반은 400 {@code PROMOTION_INVALID_INPUT}.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Idempotent
    public CreateCouponResponse create(
            Principal principal, @Valid @RequestBody CreateCouponRequest request) {
        return new CreateCouponResponse(
                create.execute(request.toCommand(UUID.fromString(principal.getName()), null)));
    }

    /** 쿠폰(플랫폼·판매자) 발급을 멈춘다. 이미 멈췄으면 204. 없으면 404 {@code PROMOTION_COUPON_NOT_FOUND}. */
    @PostMapping("/{couponId}/stop")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void stop(Principal principal, @PathVariable UUID couponId) {
        stop.execute(new StopCouponCommand(UUID.fromString(principal.getName()), null, couponId));
    }
}
