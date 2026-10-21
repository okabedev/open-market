package com.openmarket.order.command.application;

import java.util.UUID;

/** 결제 전 구매자 취소. */
public record CancelOrderByBuyerCommand(UUID buyerId, UUID orderId) {}
