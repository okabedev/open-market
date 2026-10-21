package com.openmarket.fulfillment.command.domain;

import com.openmarket.common.core.error.NotFoundException;

/** 하위 주문의 배송이 없거나(결제 직후 포함) 다른 판매자의 것이다. 404다. */
public class ShipmentNotFoundException extends NotFoundException {

    public ShipmentNotFoundException() {
        super("FULFILLMENT_NOT_FOUND", "배송 정보를 찾을 수 없습니다.");
    }
}
