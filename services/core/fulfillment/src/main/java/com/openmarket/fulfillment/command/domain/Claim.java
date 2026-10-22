package com.openmarket.fulfillment.command.domain;

import com.openmarket.common.core.id.Ids;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 클레임(docs/glossary.md "클레임"). 하위 주문 전체가 대상이고, 신청 때 하위 주문의 마지막 배송을 가리킨다. 상태 규칙은
 * docs/architecture/fulfillment.md "클레임".
 */
@Entity
@Table(name = "claim")
public class Claim {

    @Id private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClaimType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClaimStatus status;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "seller_order_id", nullable = false)
    private UUID sellerOrderId;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(name = "buyer_id", nullable = false)
    private UUID buyerId;

    @Column(name = "shipment_id", nullable = false)
    private UUID shipmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClaimReason reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Fault fault;

    @Column(name = "refund_amount", nullable = false)
    private long refundAmount;

    // 마지막 환불 실패 코드(RefundFailed). 환불이 끝나면 지운다.
    @Column(name = "refund_failure_code")
    private @Nullable String refundFailureCode;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "approved_at")
    private @Nullable Instant approvedAt;

    @Column(name = "completed_at")
    private @Nullable Instant completedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version private long version;

    protected Claim() {}

    private Claim(
            ClaimType type, Shipment shipment, ClaimReason reason, long refundAmount, Instant now) {
        this.id = Ids.newId();
        this.type = type;
        this.status = ClaimStatus.REQUESTED;
        this.orderId = shipment.orderId();
        this.sellerOrderId = shipment.sellerOrderId();
        this.sellerId = shipment.sellerId();
        this.buyerId = shipment.buyerId();
        this.shipmentId = shipment.id();
        this.reason = reason;
        this.fault = reason.fault();
        this.refundAmount = refundAmount;
        this.requestedAt = now;
        this.updatedAt = now;
    }

    /**
     * 발송 전 취소(CLM-1). 배송을 {@code CANCELLED}로 바꾸고, 하위 주문 금액 전액을 환불하는 클레임을 신청과 동시에 승인한다.
     *
     * @throws ShipmentInvalidStateException 배송이 발송 전({@code READY}, {@code ACCEPTED})이 아니다
     */
    public static Claim cancel(Shipment shipment, Instant now) {
        shipment.cancel(now);
        Claim claim =
                new Claim(
                        ClaimType.CANCEL,
                        shipment,
                        ClaimReason.BUYER_CANCEL,
                        shipment.amount(),
                        now);
        claim.status = ClaimStatus.APPROVED;
        claim.approvedAt = now;
        return claim;
    }

    /** 환불(또는 교환 재발송)이 끝났다. {@code APPROVED}에서만 바뀌고, 그 밖에는 아무것도 하지 않고 {@code false}. */
    public boolean complete(Instant now) {
        if (status != ClaimStatus.APPROVED) {
            return false;
        }
        status = ClaimStatus.COMPLETED;
        completedAt = now;
        refundFailureCode = null;
        updatedAt = now;
        return true;
    }

    /** 환불 실패 코드를 남긴다. 클레임은 {@code APPROVED}로 남는다. {@code APPROVED}가 아니면 {@code false}. */
    public boolean recordRefundFailure(String code, Instant now) {
        if (status != ClaimStatus.APPROVED) {
            return false;
        }
        refundFailureCode = code;
        updatedAt = now;
        return true;
    }

    public boolean isOngoing() {
        return status.isOngoing();
    }

    public UUID id() {
        return id;
    }

    public ClaimType type() {
        return type;
    }

    public ClaimStatus status() {
        return status;
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

    public UUID shipmentId() {
        return shipmentId;
    }

    public ClaimReason reason() {
        return reason;
    }

    public Fault fault() {
        return fault;
    }

    public long refundAmount() {
        return refundAmount;
    }

    public @Nullable String refundFailureCode() {
        return refundFailureCode;
    }

    public Instant requestedAt() {
        return requestedAt;
    }

    public @Nullable Instant approvedAt() {
        return approvedAt;
    }

    public @Nullable Instant completedAt() {
        return completedAt;
    }
}
