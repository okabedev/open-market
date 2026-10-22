package com.openmarket.fulfillment.command.domain;

import com.openmarket.common.core.id.Ids;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
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

    @Embedded private @Nullable TrackingInfo trackingInfo;

    @Column(name = "paid_at", nullable = false)
    private Instant paidAt;

    @Column(name = "accepted_at")
    private @Nullable Instant acceptedAt;

    @Column(name = "shipped_at")
    private @Nullable Instant shippedAt;

    @Column(name = "in_transit_at")
    private @Nullable Instant inTransitAt;

    @Column(name = "delivered_at")
    private @Nullable Instant deliveredAt;

    // 자동 구매확정 기한. 배송 완료 때 정한다.
    @Column(name = "confirm_due_at")
    private @Nullable Instant confirmDueAt;

    @Column(name = "cancelled_at")
    private @Nullable Instant cancelledAt;

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

    /** 교환 재발송({@link Claim#reship}). 대상 배송과 같은 항목·금액의 새 배송이고 교환 클레임을 가리킨다. */
    static Shipment reship(Shipment target, UUID claimId, Instant now) {
        Shipment shipment =
                new Shipment(
                        target.orderId,
                        target.sellerOrderId,
                        target.sellerId,
                        target.buyerId,
                        target.amount,
                        target.lines.stream().map(ShipmentLine::copy).toList(),
                        target.paidAt,
                        now);
        shipment.claimId = claimId;
        return shipment;
    }

    /** 발주 확인(FUL-1). {@code READY}에서만 한다. 이미 {@code ACCEPTED}면 그대로다. */
    public Transition accept(Instant now) {
        if (status == ShipmentStatus.ACCEPTED) {
            return Transition.UNCHANGED;
        }
        if (status != ShipmentStatus.READY) {
            throw new ShipmentInvalidStateException(status, "발주를 확인할 수 없는 상태입니다.");
        }
        status = ShipmentStatus.ACCEPTED;
        acceptedAt = now;
        updatedAt = now;
        return Transition.CHANGED;
    }

    /** 발송(FUL-1). {@code ACCEPTED}에서만 한다. 이미 발송했고 송장이 같으면 그대로다. 송장이 다르면 409다(송장을 바꾸는 기능은 없다). */
    public Transition ship(TrackingInfo tracking, Instant now) {
        if (shippedAt != null) {
            if (tracking.equals(trackingInfo)) {
                return Transition.UNCHANGED;
            }
            throw new ShipmentInvalidStateException(status, "이미 다른 송장으로 발송했습니다.");
        }
        if (status != ShipmentStatus.ACCEPTED) {
            throw new ShipmentInvalidStateException(status, "발송할 수 없는 상태입니다. 발주를 먼저 확인하세요.");
        }
        status = ShipmentStatus.SHIPPED;
        trackingInfo = tracking;
        shippedAt = now;
        updatedAt = now;
        return Transition.CHANGED;
    }

    /** 배송 시뮬레이터: 발송 → 배송 중. {@code SHIPPED}가 아니면 아무것도 하지 않고 {@code false}. */
    public boolean startTransit(Instant now) {
        if (status != ShipmentStatus.SHIPPED) {
            return false;
        }
        status = ShipmentStatus.IN_TRANSIT;
        inTransitAt = now;
        updatedAt = now;
        return true;
    }

    /**
     * 배송 시뮬레이터: 배송 중 → 배송 완료. 자동 구매확정 기한을 배송 완료 시각 + {@code autoConfirmAfter}로 정한다. {@code
     * IN_TRANSIT}이 아니면 아무것도 하지 않고 {@code false}.
     */
    public boolean deliver(Instant now, Duration autoConfirmAfter) {
        if (status != ShipmentStatus.IN_TRANSIT) {
            return false;
        }
        status = ShipmentStatus.DELIVERED;
        deliveredAt = now;
        confirmDueAt = now.plus(autoConfirmAfter);
        updatedAt = now;
        return true;
    }

    /** 자동 구매확정 기한을 {@code until}까지 늦춘다(반품·교환 거부 뒤 중재 요청 기한). 기한이 없거나 이미 더 늦으면 그대로다. */
    public void extendConfirmDue(Instant until) {
        if (confirmDueAt != null && confirmDueAt.isBefore(until)) {
            confirmDueAt = until;
        }
    }

    /** 발송 전 취소({@link Claim#cancel}). {@code READY}, {@code ACCEPTED}에서만 한다. */
    void cancel(Instant now) {
        if (status != ShipmentStatus.READY && status != ShipmentStatus.ACCEPTED) {
            throw new ShipmentInvalidStateException(
                    status, "발송한 뒤에는 취소할 수 없습니다. 배송이 끝나면 반품을 신청하세요.");
        }
        status = ShipmentStatus.CANCELLED;
        cancelledAt = now;
        updatedAt = now;
    }

    /** 이 배송을 받는 구매자인가. */
    public boolean isReceivedBy(UUID buyerId) {
        return this.buyerId.equals(buyerId);
    }

    /** 이 배송을 처리하는 판매자인가. */
    public boolean isHandledBy(UUID sellerId) {
        return this.sellerId.equals(sellerId);
    }

    public UUID id() {
        return id;
    }

    public UUID orderId() {
        return orderId;
    }

    /** 교환 재발송이면 그 교환 클레임. 결제로 만든 첫 배송은 비어 있다. */
    public @Nullable UUID claimId() {
        return claimId;
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

    public @Nullable TrackingInfo trackingInfo() {
        return trackingInfo;
    }

    public @Nullable Instant acceptedAt() {
        return acceptedAt;
    }

    public @Nullable Instant shippedAt() {
        return shippedAt;
    }

    public @Nullable Instant inTransitAt() {
        return inTransitAt;
    }

    public @Nullable Instant deliveredAt() {
        return deliveredAt;
    }

    public @Nullable Instant confirmDueAt() {
        return confirmDueAt;
    }

    public @Nullable Instant cancelledAt() {
        return cancelledAt;
    }

    /** 판매자 동작의 결과. 같은 동작을 다시 하면 {@code UNCHANGED}이고 이벤트를 내지 않는다. */
    public enum Transition {
        CHANGED,
        UNCHANGED
    }
}
