package com.openmarket.promotion.ui.web;

import com.openmarket.promotion.command.application.CalculateCouponDiscountUseCase;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 서비스 간 할인 계산(docs/architecture/promotion.md "할인 계산"). order가 주문을 만들 때 부른다. {@code internal} scope
 * 토큰만 부른다(common-security). gateway는 {@code /internal/**}을 넘기지 않는다. 상태를 바꾸지 않는 조회지만 본문이 있어 POST다.
 */
@RestController
public class InternalCouponDiscountController {

    private final CalculateCouponDiscountUseCase calculate;

    public InternalCouponDiscountController(CalculateCouponDiscountUseCase calculate) {
        this.calculate = calculate;
    }

    /**
     * 하위 주문마다 판매자 쿠폰 할인, 플랫폼 쿠폰 할인 몫, 결제 금액을 준다. 쓸 수 없는 쿠폰이 있으면 200에 {@code applicable=false}와 쿠폰마다
     * 이유를 준다(order가 422로 바꾼다). 하위 주문이 없거나 판매자가 겹치면 400 {@code PROMOTION_INVALID_INPUT}.
     */
    @PostMapping("/internal/v1/coupon-discounts")
    public CalculateCouponDiscountUseCase.Result calculate(
            @Valid @RequestBody CalculateCouponDiscountRequest request) {
        return calculate.execute(request.toCommand());
    }
}
