package com.openmarket.order.ui.web;

import java.util.UUID;

/** 만든 주문. 화면은 {@code GET /v1/orders/{orderId}}로 결제 대기가 될 때까지 기다린다. */
public record PlaceOrderResponse(UUID orderId) {}
