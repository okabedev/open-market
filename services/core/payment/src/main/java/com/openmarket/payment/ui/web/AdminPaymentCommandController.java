package com.openmarket.payment.ui.web;

import com.openmarket.payment.command.application.RetryPaymentCancelCommand;
import com.openmarket.payment.command.application.RetryPaymentCancelUseCase;
import com.openmarket.payment.command.application.RetryRefundCommand;
import com.openmarket.payment.command.application.RetryRefundUseCase;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 재시도(docs/architecture/payment.md "관리자 재시도"). 경로 접두어로 {@code ADMIN} 역할을 검사한다. 이미 재시도 중이면 그대로
 * 204라 {@code Idempotency-Key}는 받지 않는다.
 */
@RestController
public class AdminPaymentCommandController {

    private final RetryRefundUseCase retryRefund;
    private final RetryPaymentCancelUseCase retryCancel;

    public AdminPaymentCommandController(
            RetryRefundUseCase retryRefund, RetryPaymentCancelUseCase retryCancel) {
        this.retryRefund = retryRefund;
        this.retryCancel = retryCancel;
    }

    /**
     * 환불 재시도. 다시 확인해 통과하면 {@code REQUESTED}로 돌린다. 끝난 환불은 409 {@code PAYMENT_NOT_RETRYABLE}, 여전히 환불할
     * 수 없으면 409 {@code PAYMENT_NOT_REFUNDABLE} 또는 {@code PAYMENT_REFUND_AMOUNT_EXCEEDED}, 없으면 404
     * {@code PAYMENT_REFUND_NOT_FOUND}.
     */
    @PostMapping("/v1/admin/refunds/{refundId}/retry")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retryRefund(Principal principal, @PathVariable UUID refundId) {
        retryRefund.execute(new RetryRefundCommand(adminId(principal), refundId));
    }

    /**
     * 보상 취소 재시도. 다시 시도할 취소가 없으면 409 {@code PAYMENT_NOT_RETRYABLE}, 없으면 404 {@code
     * PAYMENT_NOT_FOUND}.
     */
    @PostMapping("/v1/admin/payments/{paymentId}/cancel-retry")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retryCancel(Principal principal, @PathVariable UUID paymentId) {
        retryCancel.execute(new RetryPaymentCancelCommand(adminId(principal), paymentId));
    }

    private static UUID adminId(Principal principal) {
        return UUID.fromString(principal.getName());
    }
}
