package com.openmarket.payment.ui.web;

import com.openmarket.payment.query.application.PaymentQueryService;
import com.openmarket.payment.query.dto.PaymentView;
import java.security.Principal;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 결제 조회. 주체는 토큰의 {@code sub}(= buyerId)다. 규칙은 docs/architecture/payment.md "조회". */
@RestController
public class PaymentQueryController {

    private final PaymentQueryService payments;

    public PaymentQueryController(PaymentQueryService payments) {
        this.payments = payments;
    }

    /**
     * 본인 주문의 결제 상태와 금액. 결제는 주문이 결제 대기가 된 뒤 이벤트로 만들어지므로, 그 사이와 다른 회원의 주문은 404 {@code
     * PAYMENT_NOT_FOUND}다.
     */
    @GetMapping("/v1/payments/orders/{orderId}")
    public PaymentView payment(Principal principal, @PathVariable UUID orderId) {
        return payments.buyerPayment(UUID.fromString(principal.getName()), orderId);
    }
}
