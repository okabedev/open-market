package com.openmarket.fulfillment.command.domain;

import com.openmarket.common.core.id.Ids;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** 구매확정(docs/glossary.md "구매확정"). 하위 주문당 한 번이고(유일 인덱스), 확정한 배송을 가리킨다. 확정한 뒤에는 바꾸지 않는다. */
@Entity
@Table(name = "purchase_confirmation")
public class PurchaseConfirmation {

    @Id private UUID id;

    @Column(name = "seller_order_id", nullable = false, unique = true)
    private UUID sellerOrderId;

    @Column(name = "shipment_id", nullable = false)
    private UUID shipmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConfirmMode mode;

    @Column(name = "confirmed_at", nullable = false)
    private Instant confirmedAt;

    protected PurchaseConfirmation() {}

    private PurchaseConfirmation(Shipment shipment, ConfirmMode mode, Instant now) {
        this.id = Ids.newId();
        this.sellerOrderId = shipment.sellerOrderId();
        this.shipmentId = shipment.id();
        this.mode = mode;
        this.confirmedAt = now;
    }

    /** 배송 완료된 배송을 확정한다. 배송 완료 전이면 409 {@code FULFILLMENT_INVALID_STATE}. */
    public static PurchaseConfirmation of(Shipment shipment, ConfirmMode mode, Instant now) {
        if (shipment.status() != ShipmentStatus.DELIVERED) {
            throw new ShipmentInvalidStateException(shipment.status(), "배송이 끝난 뒤에 구매를 확정할 수 있습니다.");
        }
        return new PurchaseConfirmation(shipment, mode, now);
    }

    public UUID id() {
        return id;
    }

    public UUID sellerOrderId() {
        return sellerOrderId;
    }

    public UUID shipmentId() {
        return shipmentId;
    }

    public ConfirmMode mode() {
        return mode;
    }

    public Instant confirmedAt() {
        return confirmedAt;
    }
}
