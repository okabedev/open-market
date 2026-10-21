package com.openmarket.fulfillment.query.dto;

/** 조회용 배송 상태. 상수는 커맨드 쪽 {@code ShipmentStatus}와 같다. */
public enum ShipmentStatus {
    READY,
    ACCEPTED,
    SHIPPED,
    IN_TRANSIT,
    DELIVERED,
    CANCELLED
}
