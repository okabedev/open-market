package com.openmarket.payment.ui.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * 결제 승인 요청. 토스 {@code successUrl}로 받은 값이다.
 *
 * @param amount 결제 금액(원)
 */
public record ConfirmPaymentRequest(
        @NotNull UUID orderId,
        @NotBlank @Size(max = 200) String paymentKey,
        @NotNull @PositiveOrZero Long amount) {}
