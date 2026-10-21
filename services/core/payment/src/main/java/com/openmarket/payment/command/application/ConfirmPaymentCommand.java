package com.openmarket.payment.command.application;

import java.util.UUID;

/** 토스 {@code successUrl}로 받은 값으로 승인을 요청한다. 금액은 원이다. */
public record ConfirmPaymentCommand(UUID buyerId, UUID orderId, String paymentKey, long amount) {}
