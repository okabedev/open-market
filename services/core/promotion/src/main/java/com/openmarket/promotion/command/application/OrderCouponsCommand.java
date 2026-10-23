package com.openmarket.promotion.command.application;

import java.util.UUID;

/** 주문에 묶인 쿠폰의 사용({@code OrderPaid})·복원({@code OrderCancelled}). */
public record OrderCouponsCommand(UUID orderId) {}
