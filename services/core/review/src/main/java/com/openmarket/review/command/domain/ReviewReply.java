package com.openmarket.review.command.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * 답글(docs/glossary.md "답글", REV-3). 리뷰 상품의 판매자가 리뷰마다 하나 달고 고칠 수 있다. ID는 리뷰 ID다. 규칙은
 * docs/architecture/review.md "답글".
 */
@Entity
@Table(name = "review_reply")
public class ReviewReply {

    static final int CONTENT_MAX = 1_000;

    @Id
    @Column(name = "review_id")
    private UUID reviewId;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(nullable = false)
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version private long version;

    protected ReviewReply() {}

    private ReviewReply(Review review, String content, Instant now) {
        this.reviewId = review.id();
        this.sellerId = review.sellerId();
        this.content = content;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * 리뷰에 첫 답글을 단다. 숨긴 리뷰에도 단다.
     *
     * <ul>
     *   <li>다른 판매자의 상품 리뷰면 404 {@code REVIEW_NOT_FOUND}
     *   <li>본문은 앞뒤 공백을 지우고 1~1,000자. 아니면 400 {@code REVIEW_INVALID_INPUT}
     * </ul>
     */
    public static ReviewReply write(Review review, UUID sellerId, String content, Instant now) {
        if (!review.isSoldBy(sellerId)) {
            throw new ReviewNotFoundException();
        }
        return new ReviewReply(review, checked(content), now);
    }

    /** 답글을 고친다. 같은 본문이면 false이고 아무것도 바꾸지 않는다. */
    public boolean edit(String content, Instant now) {
        String text = checked(content);
        if (text.equals(this.content)) {
            return false;
        }
        this.content = text;
        this.updatedAt = now;
        return true;
    }

    private static String checked(String content) {
        String text = content.strip();
        if (text.isEmpty() || text.length() > CONTENT_MAX) {
            throw new InvalidReviewException("답글은 1~1,000자로 써 주세요.");
        }
        return text;
    }

    public UUID reviewId() {
        return reviewId;
    }

    public UUID sellerId() {
        return sellerId;
    }

    public String content() {
        return content;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
