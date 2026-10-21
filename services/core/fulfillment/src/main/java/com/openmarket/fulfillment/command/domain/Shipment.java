package com.openmarket.fulfillment.command.domain;

import com.openmarket.common.core.id.Ids;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 배송(docs/glossary.md "배송"). 하위 주문의 금액과 항목을 결제 시점 그대로 고정한다. 상태 규칙은 docs/architecture/fulfillment.md
 * "배송".
 */
@Entity
@Table(name = "shipment")
public class Shipment {

    @Id private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "seller_order_id", nullable = false)
    private UUID sellerOrderId;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(name = "buyer_id", nullable = false)
    private UUID buyerId;

    @Column(name = "claim_id")
    private @Nullable UUID claimId;

    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShipmentStatus status;

    @ElementCollection
    @CollectionTable(name = "shipment_line", joinColumns = @JoinColumn(name = "shipment_id"))
    @OrderColumn(name = "position")
    private List<ShipmentLine> lines = new ArrayList<>();

    @Column(name = "paid_at", nullable = false)
    private Instant paidAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version private long version;

    protected Shipment() {}

    private Shipment(
            UUID orderId,
            UUID sellerOrderId,
            UUID sellerId,
            UUID buyerId,
            long amount,
            List<ShipmentLine> lines,
            Instant paidAt,
            Instant now) {
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("배송 항목이 없다: sellerOrderId=" + sellerOrderId);
        }
        this.id = Ids.newId();
        this.orderId = orderId;
        this.sellerOrderId = sellerOrderId;
        this.sellerId = sellerId;
        this.buyerId = buyerId;
        this.amount = amount;
        this.status = ShipmentStatus.READY;
        this.lines = new ArrayList<>(lines);
        this.paidAt = paidAt;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** 결제된 하위 주문의 첫 배송({@code OrderPaid}). 판매자의 발주 확인을 기다린다. */
    public static Shipment ready(
            UUID orderId,
            UUID sellerOrderId,
            UUID sellerId,
            UUID buyerId,
            long amount,
            List<ShipmentLine> lines,
            Instant paidAt,
            Instant now) {
        return new Shipment(orderId, sellerOrderId, sellerId, buyerId, amount, lines, paidAt, now);
    }

    public UUID id() {
        return id;
    }

    public UUID orderId() {
        return orderId;
    }

    public UUID sellerOrderId() {
        return sellerOrderId;
    }

    public UUID sellerId() {
        return sellerId;
    }

    public UUID buyerId() {
        return buyerId;
    }

    public long amount() {
        return amount;
    }

    public ShipmentStatus status() {
        return status;
    }

    public List<ShipmentLine> lines() {
        return List.copyOf(lines);
    }

    public Instant paidAt() {
        return paidAt;
    }
}
