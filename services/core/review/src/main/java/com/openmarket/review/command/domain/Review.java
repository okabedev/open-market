package com.openmarket.review.command.domain;

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
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;

/**
 * 리뷰(docs/glossary.md "리뷰", REV-1). 주문 항목마다 하나이고(DB 유니크) 쓴 뒤 고치거나 지우지 않는다. 규칙은
 * docs/architecture/review.md "리뷰".
 */
@Entity
@Table(name = "review")
public class Review {

    public static final int RATING_MIN = 1;
    public static final int RATING_MAX = 5;
    static final int CONTENT_MIN = 10;
    static final int CONTENT_MAX = 1_000;
    static final int FILES_MAX = 5;

    @Id private UUID id;

    @Column(name = "order_line_id", nullable = false)
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

    @Column(nullable = false)
    private int rating;

    @Column(nullable = false)
    private String content;

    @ElementCollection
    @CollectionTable(name = "review_file", joinColumns = @JoinColumn(name = "review_id"))
    @OrderBy("position")
    private List<ReviewFile> files = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version private long version;

    protected Review() {}

    private Review(ReviewEligibility eligibility, int rating, String content, Instant now) {
        this.id = Ids.newId();
        this.orderLineId = eligibility.orderLineId();
        this.orderId = eligibility.orderId();
        this.sellerOrderId = eligibility.sellerOrderId();
        this.productId = eligibility.productId();
        this.productName = eligibility.productName();
        List<String> options = eligibility.optionValues();
        this.optionValue1 = options.isEmpty() ? null : options.get(0);
        this.optionValue2 = options.size() < 2 ? null : options.get(1);
        this.sellerId = eligibility.sellerId();
        this.buyerId = eligibility.buyerId();
        this.rating = rating;
        this.content = content;
        this.status = ReviewStatus.VISIBLE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * 작성 자격의 주문 항목에 리뷰를 쓴다. 자격에 리뷰 ID를 남긴다.
     *
     * <ul>
     *   <li>다른 구매자의 자격이면 404 {@code REVIEW_NOT_ELIGIBLE}
     *   <li>이미 썼으면 409 {@code REVIEW_ALREADY_WRITTEN}
     *   <li>별점 1~5, 본문 10~1,000자(앞뒤 공백을 지운다), 사진 0~5장 중복 없이. 아니면 400 {@code REVIEW_INVALID_INPUT}
     * </ul>
     */
    public static Review write(
            ReviewEligibility eligibility,
            UUID buyerId,
            int rating,
            String content,
            List<UUID> fileIds,
            Instant now) {
        if (!eligibility.belongsTo(buyerId)) {
            throw new ReviewNotEligibleException();
        }
        if (eligibility.isWritten()) {
            throw new ReviewAlreadyWrittenException();
        }
        if (rating < RATING_MIN || rating > RATING_MAX) {
            throw new InvalidReviewException("별점은 1~5입니다.");
        }
        String text = content.strip();
        if (text.length() < CONTENT_MIN || text.length() > CONTENT_MAX) {
            throw new InvalidReviewException("리뷰는 10~1,000자로 써 주세요.");
        }
        if (fileIds.size() > FILES_MAX || new HashSet<>(fileIds).size() != fileIds.size()) {
            throw new InvalidReviewException("사진은 같은 사진 없이 5장까지 넣을 수 있습니다.");
        }
        Review review = new Review(eligibility, rating, text, now);
        review.files =
                new ArrayList<>(
                        IntStream.range(0, fileIds.size())
                                .mapToObj(i -> new ReviewFile(i, fileIds.get(i)))
                                .toList());
        eligibility.markWritten(review.id, now);
        return review;
    }

    /** 관리자가 숨긴다. 이미 숨겼으면 false이고 아무것도 바꾸지 않는다. */
    public boolean hide(Instant now) {
        return changeStatus(ReviewStatus.HIDDEN, now);
    }

    /** 관리자가 다시 보인다. 이미 보이면 false이고 아무것도 바꾸지 않는다. */
    public boolean show(Instant now) {
        return changeStatus(ReviewStatus.VISIBLE, now);
    }

    private boolean changeStatus(ReviewStatus to, Instant now) {
        if (status == to) {
            return false;
        }
        status = to;
        updatedAt = now;
        return true;
    }

    /** 이 판매자의 상품 리뷰인가. */
    public boolean isSoldBy(UUID sellerId) {
        return this.sellerId.equals(sellerId);
    }

    public UUID id() {
        return id;
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

    public int rating() {
        return rating;
    }

    public String content() {
        return content;
    }

    public List<UUID> fileIds() {
        return files.stream().map(ReviewFile::fileId).toList();
    }

    public ReviewStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
