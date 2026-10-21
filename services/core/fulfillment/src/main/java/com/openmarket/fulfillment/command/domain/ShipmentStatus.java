package com.openmarket.fulfillment.command.domain;

/** 배송 상태(docs/architecture/fulfillment.md "배송"). */
public enum ShipmentStatus {
    READY,
    ACCEPTED,
    SHIPPED,
    IN_TRANSIT,
    DELIVERED,
    CANCELLED
}
