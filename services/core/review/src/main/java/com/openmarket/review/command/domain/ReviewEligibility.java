package com.openmarket.review.command.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 리뷰 작성 자격(docs/glossary.md "리뷰 작성 자격"). 구매확정한 주문 항목마다 하나이고 ID는 주문 항목 ID다. 규칙은
 * docs/architecture/review.md "리뷰 작성 자격".
 */
@Entity
@Table(name = "review_eligibility")
public class ReviewEligibility {

    @Id
    @Column(name = "order_line_id")
    private UUID orderLineId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "seller_order_id", nullable = false)
    private UUID sellerOrderId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "option_value_1")
    private @Nullable String optionValue1;

    @Column(name = "option_value_2")
    private @Nullable String optionValue2;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(name = "buyer_id", nullable = false)
    private UUID buyerId;

    @Column(name = "confirmed_at", nullable = false)
    private Instant confirmedAt;

    // 리뷰를 썼으면 그 리뷰
    @Column(name = "review_id")
    private @Nullable UUID reviewId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version private long version;

    protected ReviewEligibility() {}

    private ReviewEligibility(Purchase purchase, Instant now) {
        this.orderLineId = purchase.orderLineId();
        this.orderId = purchase.orderId();
        this.sellerOrderId = purchase.sellerOrderId();
        this.productId = purchase.productId();
        this.productName = purchase.productName();
        List<String> options = purchase.optionValues();
        this.optionValue1 = options.isEmpty() ? null : options.get(0);
        this.optionValue2 = options.size() < 2 ? null : options.get(1);
        this.sellerId = purchase.sellerId();
        this.buyerId = purchase.buyerId();
        this.confirmedAt = purchase.confirmedAt();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** 구매확정한 주문 항목({@code PurchaseConfirmed}의 항목 하나). */
    public static ReviewEligibility create(Purchase purchase, Instant now) {
        return new ReviewEligibility(purchase, now);
    }

    /** 이 구매자의 자격인가. */
    public boolean belongsTo(UUID buyerId) {
        return this.buyerId.equals(buyerId);
    }

    public boolean isWritten() {
        return reviewId != null;
    }

    /** 리뷰를 썼다. {@link Review#write}만 부른다. */
    void markWritten(UUID reviewId, Instant now) {
        if (this.reviewId != null) {
            throw new ReviewAlreadyWrittenException();
        }
        this.reviewId = reviewId;
        this.updatedAt = now;
    }

    public UUID orderLineId() {
        return orderLineId;
    }

    public UUID orderId() {
        return orderId;
    }

    public UUID sellerOrderId() {
        return sellerOrderId;
    }

    public UUID productId() {
        return productId;
    }

    public String productName() {
        return productName;
    }

    public List<String> optionValues() {
        List<String> values = new ArrayList<>(2);
        if (optionValue1 != null) {
            values.add(optionValue1);
        }
        if (optionValue2 != null) {
            values.add(optionValue2);
        }
        return List.copyOf(values);
    }

    public UUID sellerId() {
        return sellerId;
    }

    public UUID buyerId() {
        return buyerId;
    }

    public Instant confirmedAt() {
        return confirmedAt;
    }

    public @Nullable UUID reviewId() {
        return reviewId;
    }

    /**
     * 구매확정한 주문 항목 하나. 옵션은 두 개까지다(catalog 옵션 규칙).
     *
     * @param optionValues 옵션 값. 옵션이 없는 상품이면 빈 목록
     */
    public record Purchase(
            UUID orderLineId,
            UUID orderId,
            UUID sellerOrderId,
            UUID productId,
            String productName,
            List<String> optionValues,
            UUID sellerId,
            UUID buyerId,
            Instant confirmedAt) {

        public Purchase {
            if (optionValues.size() > 2) {
                throw new IllegalArgumentException("옵션은 두 개까지다: " + optionValues.size());
            }
            optionValues = List.copyOf(optionValues);
        }
    }
}
