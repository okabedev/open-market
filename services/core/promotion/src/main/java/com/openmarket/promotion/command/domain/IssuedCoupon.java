package com.openmarket.promotion.command.domain;

import com.openmarket.common.core.id.Ids;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 발급 쿠폰(docs/glossary.md "발급 쿠폰"). 쿠폰 정책과 회원의 쌍은 하나다(DB 유니크). 상태 규칙은 docs/architecture/promotion.md
 * "발급 쿠폰".
 */
@Entity
@Table(name = "issued_coupon")
public class IssuedCoupon {

    @Id private UUID id;

    @Column(name = "coupon_id", nullable = false)
    private UUID couponId;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IssuedCouponStatus status;

    // 결제 전 주문에 묶였거나 쓰였을 때 그 주문.
    @Column(name = "order_id")
    private @Nullable UUID orderId;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version private long version;

    protected IssuedCoupon() {}

    private IssuedCoupon(UUID couponId, UUID memberId, Instant now, int validDays) {
        this.id = Ids.newId();
        this.couponId = couponId;
        this.memberId = memberId;
        this.status = IssuedCouponStatus.AVAILABLE;
        this.issuedAt = now;
        this.expiresAt = now.plus(Duration.ofDays(validDays));
        this.updatedAt = now;
    }

    /** 내려받은 쿠폰. 쿠폰이 지금 발급 중이 아니면 409 {@code PROMOTION_COUPON_NOT_ISSUING}. 수량은 저장할 때 DB가 센다. */
    public static IssuedCoupon issue(Coupon coupon, UUID memberId, Instant now) {
        if (!coupon.isIssuableAt(now)) {
            throw new CouponNotIssuingException();
        }
        return new IssuedCoupon(coupon.id(), memberId, now, coupon.terms().validDays());
    }

    /** 이 구매자가 지금 주문에 쓸 수 없는 이유. 쓸 수 있으면 null. 만료 시각부터 만료다. */
    public @Nullable CouponUnavailableReason unavailableReason(UUID buyerId, Instant now) {
        if (!memberId.equals(buyerId)) {
            return CouponUnavailableReason.NOT_OWNED;
        }
        if (status != IssuedCouponStatus.AVAILABLE) {
            return CouponUnavailableReason.NOT_AVAILABLE;
        }
        if (!now.isBefore(expiresAt)) {
            return CouponUnavailableReason.EXPIRED;
        }
        return null;
    }

    /** 이 주문에 묶였거나 쓰였는가. */
    public boolean isBoundTo(UUID orderId) {
        return orderId.equals(this.orderId);
    }

    /**
     * 결제 전 주문에 묶는다({@code OrderPlaced}). 쓸 수 있는 쿠폰만 묶는다. 쓸 수 있는지는 {@link #unavailableReason}로 먼저
     * 본다.
     */
    public void reserve(UUID orderId, Instant now) {
        if (status != IssuedCouponStatus.AVAILABLE) {
            throw new IllegalStateException("쓸 수 있는 쿠폰만 묶는다: status=" + status);
        }
        status = IssuedCouponStatus.RESERVED;
        this.orderId = orderId;
        updatedAt = now;
    }

    /** 묶인 주문이 결제됐다({@code OrderPaid}). 이미 썼으면 아무것도 바꾸지 않고 {@code false}. */
    public boolean use(UUID orderId, Instant now) {
        if (!isBoundTo(orderId) || status != IssuedCouponStatus.RESERVED) {
            return false;
        }
        status = IssuedCouponStatus.USED;
        updatedAt = now;
        return true;
    }

    /**
     * 묶인 주문이 결제 전에 취소됐다({@code OrderCancelled}). 다시 쓸 수 있게 되돌린다. 만료 시각은 그대로라 지났으면 만료다. 이미 되돌렸거나 쓰인
     * 쿠폰이면 아무것도 바꾸지 않고 {@code false}.
     */
    public boolean restore(UUID orderId, Instant now) {
        if (!isBoundTo(orderId) || status != IssuedCouponStatus.RESERVED) {
            return false;
        }
        status = IssuedCouponStatus.AVAILABLE;
        this.orderId = null;
        updatedAt = now;
        return true;
    }

    public UUID id() {
        return id;
    }

    public UUID couponId() {
        return couponId;
    }

    public UUID memberId() {
        return memberId;
    }

    public IssuedCouponStatus status() {
        return status;
    }

    public @Nullable UUID orderId() {
        return orderId;
    }

    public Instant issuedAt() {
        return issuedAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
