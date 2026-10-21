package com.openmarket.payment.command.application;

import java.util.UUID;

/** 결제 전에 취소된 주문({@code OrderCancelled}). */
public record RequestPaymentCancelCommand(UUID orderId) {}
