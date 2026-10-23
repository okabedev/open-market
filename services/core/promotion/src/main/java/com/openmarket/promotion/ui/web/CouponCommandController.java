package com.openmarket.promotion.ui.web;

import com.openmarket.promotion.command.application.IssueCouponCommand;
import com.openmarket.promotion.command.application.IssueCouponUseCase;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 구매자 쿠폰 내려받기(PRM-2). 주체는 토큰의 {@code sub}(= 회원 ID)다. */
@RestController
@RequestMapping("/v1/coupons")
public class CouponCommandController {

    private final IssueCouponUseCase issue;

    public CouponCommandController(IssueCouponUseCase issue) {
        this.issue = issue;
    }

    /**
     * 선착순 쿠폰을 내려받는다. 새로 받으면 201, 이미 받은 쿠폰이면 200(같은 {@code issuedCouponId}). 상태가 정해지는 요청이라 {@code
     * Idempotency-Key}는 받지 않는다. 발급 중이 아니면 409 {@code PROMOTION_COUPON_NOT_ISSUING}, 다 나갔으면 409
     * {@code PROMOTION_COUPON_SOLD_OUT}, 발급 게이트(Redis)를 부를 수 없으면 503 {@code PROMOTION_UNAVAILABLE}.
     */
    @PostMapping("/{couponId}/issue")
    public ResponseEntity<IssueCouponResponse> issue(
            Principal principal, @PathVariable UUID couponId) {
        IssueCouponUseCase.Result result =
                issue.execute(
                        new IssueCouponCommand(UUID.fromString(principal.getName()), couponId));
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(new IssueCouponResponse(result.issuedCouponId()));
    }
}
