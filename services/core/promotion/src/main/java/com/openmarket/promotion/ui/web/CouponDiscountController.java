package com.openmarket.promotion.ui.web;

import com.openmarket.promotion.command.application.CalculateCouponDiscountUseCase;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 주문서 할인 미리보기(docs/architecture/promotion.md "할인 미리보기"). order가 주문을 만들 때와 같은 계산이다. 주체는 토큰의 {@code
 * sub}(= 회원 ID)다. 상태를 바꾸지 않는 조회지만 본문이 있어 POST다.
 */
@RestController
public class CouponDiscountController {

    private final CalculateCouponDiscountUseCase calculate;

    public CouponDiscountController(CalculateCouponDiscountUseCase calculate) {
        this.calculate = calculate;
    }

    /**
     * 하위 주문마다 판매자 쿠폰 할인, 플랫폼 쿠폰 할인 몫, 결제 금액을 준다. 쓸 수 없는 쿠폰이 있으면 200에 {@code applicable=false}와 쿠폰마다
     * 이유를 준다. 하위 주문이 없거나 판매자가 겹치면 400 {@code PROMOTION_INVALID_INPUT}.
     */
    @PostMapping("/v1/coupon-discounts")
    public CalculateCouponDiscountUseCase.Result preview(
            Principal principal, @Valid @RequestBody PreviewCouponDiscountRequest request) {
        return calculate.execute(request.toCommand(UUID.fromString(principal.getName())));
    }
}
