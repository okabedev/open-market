package com.openmarket.promotion.command.application;

import java.util.List;
import java.util.UUID;

/** 주문 쿠폰 예약({@code OrderPlaced}). */
public record ReserveCouponsCommand(UUID orderId, UUID buyerId, List<UUID> issuedCouponIds) {}
