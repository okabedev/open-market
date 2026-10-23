package com.openmarket.promotion.command.domain;

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
 * 쿠폰 정책(docs/glossary.md "쿠폰"). 조건은 만든 뒤 바꾸지 않고 발급 중지만 한다. 규칙은 docs/architecture/promotion.md "쿠폰
 * 정책".
 */
@Entity
@Table(name = "coupon")
public class Coupon {

    static final int MAX_NAME_LENGTH = 50;
    static final int MAX_QUANTITY = 1_000_000;
    static final int MAX_VALID_DAYS = 365;

    @Id private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "issuer", nullable = false)
    private CouponIssuer issuer;

    // 판매자 쿠폰만. 플랫폼 쿠폰은 비어 있다.
    @Column(name = "seller_id")
    private @Nullable UUID sellerId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false)
    private long discountValue;

    @Column(name = "max_discount")
    private @Nullable Long maxDiscount;

    @Column(name = "min_order_amount", nullable = false)
    private long minOrderAmount;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "issue_start_at", nullable = false)
    private Instant issueStartAt;

    @Column(name = "issue_end_at", nullable = false)
    private Instant issueEndAt;

    @Column(name = "valid_days", nullable = false)
    private int validDays;

    // 발급 수. 발급이 조건부 UPDATE(CouponRepository#incrementIssuedCount)로만 올린다. 엔티티는 쓰지 않는다.
    @Column(name = "issued_count", nullable = false, insertable = false, updatable = false)
    private int issuedCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponStatus status;

    @Column(name = "stopped_at")
    private @Nullable Instant stoppedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version private long version;

    protected Coupon() {}

    private Coupon(CouponIssuer issuer, @Nullable UUID sellerId, CouponTerms terms, Instant now) {
        validate(terms, now);
        this.id = Ids.newId();
        this.issuer = issuer;
        this.sellerId = sellerId;
        this.name = terms.name().strip();
        this.discountType = terms.discountType();
        this.discountValue = terms.discountValue();
        this.maxDiscount = terms.maxDiscount();
        this.minOrderAmount = terms.minOrderAmount();
        this.quantity = terms.quantity();
        this.issueStartAt = terms.issueStartAt();
        this.issueEndAt = terms.issueEndAt();
        this.validDays = terms.validDays();
        this.status = CouponStatus.ISSUING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** 관리자가 만드는 플랫폼 쿠폰. */
    public static Coupon platform(CouponTerms terms, Instant now) {
        return new Coupon(CouponIssuer.PLATFORM, null, terms, now);
    }

    /** 판매자가 만드는 판매자 쿠폰. 그 판매자의 모든 상품에 쓴다. */
    public static Coupon seller(UUID sellerId, CouponTerms terms, Instant now) {
        return new Coupon(CouponIssuer.SELLER, sellerId, terms, now);
    }

    private static void validate(CouponTerms terms, Instant now) {
        int nameLength = terms.name().strip().codePointCount(0, terms.name().strip().length());
        if (nameLength < 1 || nameLength > MAX_NAME_LENGTH) {
            throw new InvalidCouponException("쿠폰 이름은 1~50자입니다.");
        }
        switch (terms.discountType()) {
            case FIXED -> {
                if (terms.discountValue() < 1) {
                    throw new InvalidCouponException("정액 할인은 1원 이상입니다.");
                }
                if (terms.maxDiscount() != null) {
                    throw new InvalidCouponException("최대 할인은 정률 쿠폰에만 정합니다.");
                }
            }
            case RATE -> {
                if (terms.discountValue() < 1 || terms.discountValue() > 100) {
                    throw new InvalidCouponException("할인율은 1~100%입니다.");
                }
                Long maxDiscount = terms.maxDiscount();
                if (maxDiscount != null && maxDiscount < 1) {
                    throw new InvalidCouponException("최대 할인은 1원 이상입니다.");
                }
            }
        }
        if (terms.minOrderAmount() < 0) {
            throw new InvalidCouponException("최소 주문 금액은 0원 이상입니다.");
        }
        if (terms.quantity() < 1 || terms.quantity() > MAX_QUANTITY) {
            throw new InvalidCouponException("발급 수량은 1~1,000,000장입니다.");
        }
        if (!terms.issueEndAt().isAfter(terms.issueStartAt())) {
            throw new InvalidCouponException("발급 기간의 끝은 시작보다 뒤여야 합니다.");
        }
        if (!terms.issueEndAt().isAfter(now)) {
            throw new InvalidCouponException("이미 끝난 발급 기간입니다.");
        }
        if (terms.validDays() < 1 || terms.validDays() > MAX_VALID_DAYS) {
            throw new InvalidCouponException("사용 기한은 발급 후 1~365일입니다.");
        }
    }

    /** 발급 중지. 이미 중지했으면 아무것도 바꾸지 않고 {@code false}. */
    public boolean stop(Instant now) {
        if (status == CouponStatus.STOPPED) {
            return false;
        }
        status = CouponStatus.STOPPED;
        stoppedAt = now;
        updatedAt = now;
        return true;
    }

    /** 지금 내려받을 수 있는가. 발급 중이고 발급 기간(시작 포함, 끝 제외) 안이다. 수량은 따로 본다. */
    public boolean isIssuableAt(Instant now) {
        return status == CouponStatus.ISSUING
                && !now.isBefore(issueStartAt)
                && now.isBefore(issueEndAt);
    }

    /** 적용 대상 금액이 최소 주문 금액 이상인가. */
    public boolean appliesTo(long targetAmount) {
        return targetAmount >= minOrderAmount;
    }

    /**
     * 적용 대상 금액에 대한 할인(원). 정액은 할인 값, 정률은 {@code floor(대상 × 율 / 100)}을 최대 할인으로 자른 값이다. 대상 금액을 넘지 않는다.
     * 최소 주문 금액은 {@link #appliesTo}로 따로 본다.
     */
    public long discountFor(long targetAmount) {
        long discount =
                switch (discountType) {
                    case FIXED -> discountValue;
                    case RATE -> {
                        long byRate = Math.multiplyExact(targetAmount, discountValue) / 100;
                        Long cap = maxDiscount;
                        yield cap == null ? byRate : Math.min(byRate, cap);
                    }
                };
        return Math.min(discount, targetAmount);
    }

    /** 이 판매자의 판매자 쿠폰인가. */
    public boolean isOwnedBy(UUID sellerId) {
        return sellerId.equals(this.sellerId);
    }

    public UUID id() {
        return id;
    }

    public CouponIssuer issuer() {
        return issuer;
    }

    public @Nullable UUID sellerId() {
        return sellerId;
    }

    public CouponTerms terms() {
        return new CouponTerms(
                name,
                discountType,
                discountValue,
                maxDiscount,
                minOrderAmount,
                quantity,
                issueStartAt,
                issueEndAt,
                validDays);
    }

    public CouponStatus status() {
        return status;
    }

    public @Nullable Instant stoppedAt() {
        return stoppedAt;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
