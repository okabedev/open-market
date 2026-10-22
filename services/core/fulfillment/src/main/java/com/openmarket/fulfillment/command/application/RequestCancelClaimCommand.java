package com.openmarket.fulfillment.command.application;

import java.util.UUID;

/** 구매자가 발송 전 하위 주문을 취소한다(CLM-1). */
public record RequestCancelClaimCommand(UUID buyerId, UUID sellerOrderId) {}
