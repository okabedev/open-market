package com.openmarket.payment.command.application;

import java.util.UUID;

/** 결제를 기다리는 주문({@code OrderAwaitingPayment}). 금액은 원이다. */
public record PreparePaymentCommand(UUID orderId, UUID buyerId, long amount, String orderName) {}
