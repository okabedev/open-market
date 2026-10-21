package com.openmarket.fulfillment.command.application;

import java.util.UUID;

/** 판매자가 하위 주문의 발주를 확인한다(FUL-1). */
public record AcceptShipmentCommand(UUID sellerId, UUID sellerOrderId) {}
