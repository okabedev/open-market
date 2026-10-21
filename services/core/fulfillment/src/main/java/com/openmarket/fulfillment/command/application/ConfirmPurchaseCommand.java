package com.openmarket.fulfillment.command.application;

import java.util.UUID;

/** 구매자가 하위 주문을 구매확정한다(FUL-3). */
public record ConfirmPurchaseCommand(UUID buyerId, UUID sellerOrderId) {}
