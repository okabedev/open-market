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
