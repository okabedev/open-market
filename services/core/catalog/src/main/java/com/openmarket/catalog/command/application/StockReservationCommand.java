package com.openmarket.catalog.command.application;

import java.util.UUID;

/** 주문 하나의 예약을 확정하거나 푼다({@code OrderPaid}, {@code OrderCancelled}). */
public record StockReservationCommand(UUID orderId) {}
