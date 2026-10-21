package com.openmarket.fulfillment.command.domain;

import java.util.UUID;

/** 배송 저장소. 구현은 {@code infra}에 있다. */
public interface ShipmentRepository {

    Shipment save(Shipment shipment);

    /** 하위 주문의 첫 배송({@code OrderPaid}로 만든 것)이 있는가. */
    boolean existsFirstBySellerOrderId(UUID sellerOrderId);
}
