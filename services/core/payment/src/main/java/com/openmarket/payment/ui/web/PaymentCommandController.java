package com.openmarket.payment.ui.web;

import com.openmarket.common.web.idempotency.Idempotent;
import com.openmarket.payment.command.application.ConfirmPaymentCommand;
import com.openmarket.payment.command.application.ConfirmPaymentUseCase;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 결제 승인(PAY-1). 주체는 토큰의 {@code sub}(= buyerId)다. 규칙은 docs/architecture/payment.md "승인". */
@RestController
public class PaymentCommandController {

    private final ConfirmPaymentUseCase confirm;

    public PaymentCommandController(ConfirmPaymentUseCase confirm) {
        this.confirm = confirm;
    }

    /**
     * 결제를 승인한다. {@code Idempotency-Key}가 필요하다. 승인되면 200, 결과를 아직 확인하지 못했으면 202({@code
     * IN_PROGRESS})다. 토스가 거절하면 422 {@code PAYMENT_REJECTED}({@code pgCode}, {@code pgMessage}), 금액이
     * 다르면 422 {@code PAYMENT_AMOUNT_MISMATCH}, 결제가 없거나 다른 회원의 것이면 404 {@code PAYMENT_NOT_FOUND}, 다른
     * 결제 키로 이미 처리 중이거나 끝났으면 409 {@code PAYMENT_NOT_CONFIRMABLE}.
     */
    @PostMapping("/v1/payments/confirm")
    @Idempotent
    public ResponseEntity<ConfirmPaymentResponse> confirm(
            Principal principal, @Valid @RequestBody ConfirmPaymentRequest request) {
        ConfirmPaymentUseCase.Result result =
                confirm.execute(
                        new ConfirmPaymentCommand(
                                UUID.fromString(principal.getName()),
                                request.orderId(),
                                request.paymentKey(),
                                request.amount()));
        boolean approved = result.status() == ConfirmPaymentUseCase.Status.APPROVED;
        return ResponseEntity.status(approved ? HttpStatus.OK : HttpStatus.ACCEPTED)
                .body(
                        new ConfirmPaymentResponse(
                                result.paymentId(),
                                result.orderId(),
                                approved
                                        ? ConfirmPaymentResponse.Status.APPROVED
                                        : ConfirmPaymentResponse.Status.IN_PROGRESS,
                                result.amount(),
                                result.method()));
    }
}
