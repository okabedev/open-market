package com.openmarket.order.command.application;

import java.util.UUID;

/** 재고 예약이 끝난 주문({@code StockReserved}). */
public record AwaitPaymentCommand(UUID orderId) {}
