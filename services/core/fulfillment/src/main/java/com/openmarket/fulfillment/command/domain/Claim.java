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
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;

/**
 * 클레임(docs/glossary.md "클레임"). 하위 주문 전체가 대상이고, 신청 때 하위 주문의 마지막 배송을 가리킨다. 상태 규칙은
 * docs/architecture/fulfillment.md "클레임".
 */
@Entity
@Table(name = "claim")
public class Claim {

    static final int DESCRIPTION_MAX = 500;
    static final int FILES_MAX = 5;

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

    // 구매자 설명(반품·교환). 발송 전 취소는 비어 있다.
    private @Nullable String description;

    @ElementCollection
    @CollectionTable(name = "claim_file", joinColumns = @JoinColumn(name = "claim_id"))
    @OrderBy("position")
    private List<ClaimFile> files = new ArrayList<>();

    // 반품 승인 때 판매자가 고른 재입고 여부. 반품이 아니거나 승인 전이면 비어 있다.
    private @Nullable Boolean restock;

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

    @Column(name = "rejection_reason")
    private @Nullable String rejectionReason;

    @Column(name = "rejected_at")
    private @Nullable Instant rejectedAt;

    @Column(name = "mediation_requested_at")
    private @Nullable Instant mediationRequestedAt;

    // 관리자의 중재 거부 확정 사유. 판매자의 거부 사유는 rejectionReason에 남는다.
    @Column(name = "mediation_reason")
    private @Nullable String mediationReason;

    @Column(name = "mediation_decided_at")
    private @Nullable Instant mediationDecidedAt;

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

    /**
     * 반품·교환 신청(CLM-2). 배송 완료된 배송에 {@code REQUESTED}로 만든다. 귀책은 사유로 정하고, 환불 금액은 승인 때 정한다.
     *
     * @throws InvalidFulfillmentInputException 유형·사유·설명·사진 규칙(docs/architecture/fulfillment.md
     *     "반품·교환 신청")
     * @throws ShipmentInvalidStateException 배송이 {@code DELIVERED}가 아니다
     */
    public static Claim request(
            ClaimType type,
            Shipment shipment,
            ClaimReason reason,
            String description,
            List<UUID> fileIds,
            Instant now) {
        if (type == ClaimType.CANCEL || reason == ClaimReason.BUYER_CANCEL) {
            throw new InvalidFulfillmentInputException("반품이나 교환만 신청할 수 있습니다. 발송 전 취소는 취소로 하세요.");
        }
        if (type == ClaimType.EXCHANGE && reason.fault() != Fault.SELLER) {
            throw new InvalidFulfillmentInputException("교환은 상품 불량, 오배송, 상품 정보와 다름일 때만 신청할 수 있습니다.");
        }
        String text = description.strip();
        if (text.isEmpty() || text.length() > DESCRIPTION_MAX) {
            throw new InvalidFulfillmentInputException("설명은 1~500자입니다.");
        }
        if (fileIds.size() > FILES_MAX || new HashSet<>(fileIds).size() != fileIds.size()) {
            throw new InvalidFulfillmentInputException("사진은 같은 사진 없이 5장까지 넣을 수 있습니다.");
        }
        if (shipment.status() != ShipmentStatus.DELIVERED) {
            throw new ShipmentInvalidStateException(
                    shipment.status(), "배송이 끝난 뒤에 반품이나 교환을 신청할 수 있습니다.");
        }
        Claim claim = new Claim(type, shipment, reason, 0, now);
        claim.description = text;
        claim.files =
                new ArrayList<>(
                        IntStream.range(0, fileIds.size())
                                .mapToObj(i -> new ClaimFile(i, fileIds.get(i)))
                                .toList());
        return claim;
    }

    /**
     * 판매자 승인(CLM-3). 반품은 재입고 여부를 남기고 환불 금액을 정한다(docs/architecture/fulfillment.md "환불 금액"). 교환은 환불이
     * 없고, 부르는 쪽이 {@link #reship}으로 새 배송을 만든다.
     *
     * @param target 클레임의 대상 배송
     * @param restock 반품의 재입고 여부. 반품이면 필수, 교환이면 무시한다
     * @param returnShippingFee 구매자 귀책 반품에서 빼는 반품 배송비(원)
     * @return 이미 승인했으면 {@code UNCHANGED}
     * @throws ClaimInvalidStateException {@code REQUESTED}나 {@code APPROVED}가 아니다
     */
    public Approval approve(
            Shipment target, @Nullable Boolean restock, long returnShippingFee, Instant now) {
        if (status == ClaimStatus.APPROVED) {
            return Approval.UNCHANGED;
        }
        if (status != ClaimStatus.REQUESTED) {
            throw new ClaimInvalidStateException(status, "승인할 수 없는 클레임입니다.");
        }
        if (type == ClaimType.RETURN && restock == null) {
            throw new InvalidFulfillmentInputException("반품을 승인할 때는 재입고 여부를 고르세요.");
        }
        applyApproval(target, restock, returnShippingFee, now);
        return Approval.APPROVED;
    }

    /**
     * 구매자 중재 요청. 거부된 클레임에 거부 시각 + {@code window} 안에 한 번만 할 수 있다.
     *
     * @return 처음 요청하면 {@code true}, 이미 중재 중이면 {@code false}
     * @throws MediationClosedException 기한이 지났다
     * @throws ClaimInvalidStateException 거부되지 않았거나 이미 중재를 거쳤다
     */
    public boolean requestMediation(Instant now, Duration window) {
        if (status == ClaimStatus.MEDIATING) {
            return false;
        }
        if (status != ClaimStatus.REJECTED || mediationRequestedAt != null) {
            throw new ClaimInvalidStateException(status, "중재를 요청할 수 없는 클레임입니다.");
        }
        if (now.isAfter(Objects.requireNonNull(rejectedAt).plus(window))) {
            throw new MediationClosedException();
        }
        status = ClaimStatus.MEDIATING;
        mediationRequestedAt = now;
        updatedAt = now;
        return true;
    }

    /**
     * 관리자 중재 승인. 관리자가 고른 귀책으로 환불 금액을 정하고 반품은 재입고하지 않는다. 교환은 판매자 귀책이어야 한다.
     *
     * @return 이미 승인했으면 {@code UNCHANGED}
     * @throws ClaimInvalidStateException 중재 중이거나 승인된 클레임이 아니다
     */
    public Approval approveByMediation(
            Shipment target, Fault fault, long returnShippingFee, Instant now) {
        if (status == ClaimStatus.APPROVED) {
            return Approval.UNCHANGED;
        }
        if (status != ClaimStatus.MEDIATING) {
            throw new ClaimInvalidStateException(status, "중재 중인 클레임이 아닙니다.");
        }
        if (type == ClaimType.EXCHANGE && fault != Fault.SELLER) {
            throw new InvalidFulfillmentInputException("교환은 판매자 귀책일 때만 승인할 수 있습니다.");
        }
        this.fault = fault;
        applyApproval(target, false, returnShippingFee, now);
        mediationDecidedAt = now;
        return Approval.APPROVED;
    }

    /**
     * 관리자 중재 거부 확정. 사유(1~500자)를 판매자의 거부 사유와 따로 남긴다. 다시 중재를 요청할 수 없다.
     *
     * @return 처음 확정하면 {@code true}, 이미 확정했으면 {@code false}
     * @throws ClaimInvalidStateException 중재 중이거나 중재로 거부된 클레임이 아니다
     */
    public boolean rejectByMediation(String reason, Instant now) {
        if (status == ClaimStatus.REJECTED && mediationDecidedAt != null) {
            return false;
        }
        if (status != ClaimStatus.MEDIATING) {
            throw new ClaimInvalidStateException(status, "중재 중인 클레임이 아닙니다.");
        }
        String text = reason.strip();
        if (text.isEmpty() || text.length() > DESCRIPTION_MAX) {
            throw new InvalidFulfillmentInputException("거부 사유는 1~500자입니다.");
        }
        status = ClaimStatus.REJECTED;
        mediationReason = text;
        mediationDecidedAt = now;
        updatedAt = now;
        return true;
    }

    private void applyApproval(
            Shipment target, @Nullable Boolean restock, long returnShippingFee, Instant now) {
        if (type == ClaimType.RETURN) {
            long fee = fault == Fault.BUYER ? returnShippingFee : 0;
            this.restock = restock;
            this.refundAmount = Math.max(0, target.amount() - fee);
        }
        status = ClaimStatus.APPROVED;
        approvedAt = now;
        updatedAt = now;
    }

    /** 교환 승인으로 대상 배송과 같은 항목·금액의 새 배송({@code READY})을 만든다. */
    public Shipment reship(Shipment target, Instant now) {
        if (type != ClaimType.EXCHANGE || status != ClaimStatus.APPROVED) {
            throw new IllegalStateException("승인된 교환만 다시 보낸다: claimId=" + id);
        }
        return Shipment.reship(target, id, now);
    }

    /**
     * 판매자 거부(CLM-3). 사유(1~500자)를 남긴다.
     *
     * @return 처음 거부하면 {@code true}, 이미 거부했으면 {@code false}
     * @throws ClaimInvalidStateException {@code REQUESTED}나 {@code REJECTED}가 아니다
     */
    public boolean reject(String reason, Instant now) {
        if (status == ClaimStatus.REJECTED) {
            return false;
        }
        if (status != ClaimStatus.REQUESTED) {
            throw new ClaimInvalidStateException(status, "거부할 수 없는 클레임입니다.");
        }
        String text = reason.strip();
        if (text.isEmpty() || text.length() > DESCRIPTION_MAX) {
            throw new InvalidFulfillmentInputException("거부 사유는 1~500자입니다.");
        }
        status = ClaimStatus.REJECTED;
        rejectionReason = text;
        rejectedAt = now;
        updatedAt = now;
        return true;
    }

    /** 이 클레임을 처리하는 판매자인가. */
    public boolean isHandledBy(UUID sellerId) {
        return this.sellerId.equals(sellerId);
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

    public @Nullable String description() {
        return description;
    }

    public List<UUID> fileIds() {
        return files.stream().map(ClaimFile::fileId).toList();
    }

    public @Nullable Boolean restock() {
        return restock;
    }

    public @Nullable String rejectionReason() {
        return rejectionReason;
    }

    public @Nullable Instant rejectedAt() {
        return rejectedAt;
    }

    public @Nullable Instant mediationRequestedAt() {
        return mediationRequestedAt;
    }

    public @Nullable String mediationReason() {
        return mediationReason;
    }

    public @Nullable Instant mediationDecidedAt() {
        return mediationDecidedAt;
    }

    /** {@link #approve}의 결과. */
    public enum Approval {
        /** 이번에 승인했다. */
        APPROVED,
        /** 이미 승인돼 있었다. */
        UNCHANGED
    }
}
