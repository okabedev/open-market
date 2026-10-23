package com.openmarket.order.command.domain;

import com.openmarket.common.core.id.Ids;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 하위 주문(docs/glossary.md "하위 주문"). 주문을 판매자별로 나눈 단위다. {@link Order}에 딸린다. */
@Entity
@Table(name = "seller_order")
public class SellerOrder {

    @Id private UUID id;

    // Order.sellerOrders(mappedBy)의 주인 쪽이라 JPA가 order_id를 쓰는 데 쓴다. 코드에서는 읽지 않는다.
    @SuppressWarnings("UnusedVariable")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false)
    private int position;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(name = "store_name", nullable = false)
    private String storeName;

    // 상품 금액(항목 금액의 합).
    @Column(nullable = false)
    private long amount;

    // 쿠폰 할인 스냅샷(docs/architecture/promotion.md "할인 계산"). 쿠폰이 없으면 0이고 결제 금액이 상품 금액과 같다.
    @Column(name = "seller_issued_coupon_id")
    private @Nullable UUID sellerIssuedCouponId;

    @Column(name = "seller_coupon_discount", nullable = false)
    private long sellerCouponDiscount;

    @Column(name = "platform_coupon_discount", nullable = false)
    private long platformCouponDiscount;

    @Column(name = "payment_amount", nullable = false)
    private long paymentAmount;

    // 결제 전에는 비어 있다. 결제 완료가 되면 PAID부터 앞으로만 간다.
    @Enumerated(EnumType.STRING)
    @Column
    private @Nullable SellerOrderProgress progress;

    @Column(name = "progress_updated_at")
    private @Nullable Instant progressUpdatedAt;

    // 마지막 클레임(fulfillment 클레임 이벤트). 클레임이 없으면 비어 있다.
    @Column(name = "claim_id")
    private @Nullable UUID claimId;

    @Enumerated(EnumType.STRING)
    @Column(name = "claim_type")
    private @Nullable ClaimType claimType;

    @Enumerated(EnumType.STRING)
    @Column(name = "claim_status")
    private @Nullable ClaimStatus claimStatus;

    @Column(name = "claim_updated_at")
    private @Nullable Instant claimUpdatedAt;

    @OneToMany(mappedBy = "sellerOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<OrderLine> lines = new ArrayList<>();

    protected SellerOrder() {}

    SellerOrder(Order order, int position, UUID sellerId, String storeName) {
        this.id = Ids.newId();
        this.order = order;
        this.position = position;
        this.sellerId = sellerId;
        this.storeName = storeName;
    }

    void add(OrderItem item) {
        OrderLine line = new OrderLine(this, lines.size(), item);
        lines.add(line);
        amount = Math.addExact(amount, line.amount());
        paymentAmount = amount - sellerCouponDiscount - platformCouponDiscount;
    }

    /** 쿠폰 할인을 기록한다. 판매자는 부르는 쪽이 확인했다. */
    void applyDiscount(SellerOrderDiscount discount) {
        long seller = discount.sellerCouponDiscount();
        long platform = discount.platformCouponDiscount();
        if (seller < 0 || platform < 0 || Math.addExact(seller, platform) > amount) {
            throw new IllegalArgumentException("할인이 음수이거나 하위 주문 금액보다 크다");
        }
        if (discount.sellerIssuedCouponId() == null && seller != 0) {
            throw new IllegalArgumentException("판매자 쿠폰 없이 판매자 할인이 있다");
        }
        sellerIssuedCouponId = discount.sellerIssuedCouponId();
        sellerCouponDiscount = seller;
        platformCouponDiscount = platform;
        paymentAmount = amount - seller - platform;
    }

    /** 주문이 결제 완료가 됐다. */
    void startProgress(Instant now) {
        progress = SellerOrderProgress.PAID;
        progressUpdatedAt = now;
    }

    /** 진행 상태를 앞으로 바꾼다. 같거나 뒤의 단계면 아무것도 하지 않고 {@code false}. */
    boolean advance(SellerOrderProgress next, Instant now) {
        if (progress != null && next.compareTo(progress) <= 0) {
            return false;
        }
        progress = next;
        progressUpdatedAt = now;
        return true;
    }

    /**
     * 클레임 이벤트를 반영한다. 다른 클레임 ID면 새 클레임으로 바꾸고, 같은 클레임에서 같은 상태이거나 이미 끝났으면 아무것도 하지 않고 {@code false}. 발송
     * 전 취소가 끝나면 진행 상태를 {@code CANCELLED}로 바꾼다.
     */
    boolean applyClaim(UUID claimId, ClaimType type, ClaimStatus status, Instant now) {
        if (claimId.equals(this.claimId)
                && (status == claimStatus || claimStatus == ClaimStatus.COMPLETED)) {
            return false;
        }
        this.claimId = claimId;
        this.claimType = type;
        this.claimStatus = status;
        this.claimUpdatedAt = now;
        if (status == ClaimStatus.COMPLETED && type == ClaimType.CANCEL) {
            advance(SellerOrderProgress.CANCELLED, now);
        }
        if (status == ClaimStatus.COMPLETED && type == ClaimType.RETURN) {
            advance(SellerOrderProgress.RETURNED, now);
        }
        return true;
    }

    public UUID id() {
        return id;
    }

    public UUID sellerId() {
        return sellerId;
    }

    /** 상품 금액(원). */
    public long amount() {
        return amount;
    }

    public @Nullable UUID sellerIssuedCouponId() {
        return sellerIssuedCouponId;
    }

    public long sellerCouponDiscount() {
        return sellerCouponDiscount;
    }

    public long platformCouponDiscount() {
        return platformCouponDiscount;
    }

    /** 결제 금액(원). 상품 금액 − 판매자 쿠폰 할인 − 플랫폼 쿠폰 할인 몫. */
    public long paymentAmount() {
        return paymentAmount;
    }

    public @Nullable SellerOrderProgress progress() {
        return progress;
    }

    public @Nullable UUID claimId() {
        return claimId;
    }

    public @Nullable ClaimType claimType() {
        return claimType;
    }

    public @Nullable ClaimStatus claimStatus() {
        return claimStatus;
    }

    public List<OrderLine> lines() {
        return Collections.unmodifiableList(lines);
    }
}
