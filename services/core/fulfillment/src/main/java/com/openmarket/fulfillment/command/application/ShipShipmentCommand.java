package com.openmarket.fulfillment.command.application;

import java.util.UUID;

/**
 * 판매자가 송장을 입력하고 하위 주문을 발송한다(FUL-1).
 *
 * @param carrier 택배사 코드(docs/architecture/fulfillment.md "발주 확인과 발송")
 * @param trackingNumber 송장 번호. 숫자 10~14자리
 */
public record ShipShipmentCommand(
        UUID sellerId, UUID sellerOrderId, String carrier, String trackingNumber) {}
