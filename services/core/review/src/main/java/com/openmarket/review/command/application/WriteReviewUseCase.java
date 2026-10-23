package com.openmarket.review.command.application;

import com.openmarket.common.messaging.outbox.Outbox;
import com.openmarket.review.command.domain.RatingSummary;
import com.openmarket.review.command.domain.RatingSummaryChanged;
import com.openmarket.review.command.domain.RatingSummaryRepository;
import com.openmarket.review.command.domain.Review;
import com.openmarket.review.command.domain.ReviewCreated;
import com.openmarket.review.command.domain.ReviewEligibility;
import com.openmarket.review.command.domain.ReviewEligibilityRepository;
import com.openmarket.review.command.domain.ReviewNotEligibleException;
import com.openmarket.review.command.domain.ReviewRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리뷰를 쓴다(REV-1, docs/architecture/review.md "리뷰"). 작성 자격 행을 잠그고 리뷰를 저장한 뒤, 같은 트랜잭션에서 상품 별점 요약 행을 잠가
 * 바꾸고 {@code ReviewCreated}와 {@code RatingSummaryChanged}를 outbox에 쓴다. 잠그는 순서는 늘 자격 → 요약이다.
 */
@Service
public class WriteReviewUseCase {

    public static final String REVIEW_TOPIC = "review.review";
    public static final String RATING_SUMMARY_TOPIC = "review.rating-summary";

    private final ReviewEligibilityRepository eligibilities;
    private final ReviewRepository reviews;
    private final RatingSummaryRepository summaries;
    private final Outbox outbox;
    private final Clock clock;

    public WriteReviewUseCase(
            ReviewEligibilityRepository eligibilities,
            ReviewRepository reviews,
            RatingSummaryRepository summaries,
            Outbox outbox,
            Clock clock) {
        this.eligibilities = eligibilities;
        this.reviews = reviews;
        this.summaries = summaries;
        this.outbox = outbox;
        this.clock = clock;
    }

    /** 쓴 리뷰의 ID. */
    @Transactional
    public UUID execute(WriteReviewCommand command) {
        ReviewEligibility eligibility =
                eligibilities
                        .findForUpdateById(command.orderLineId())
                        .orElseThrow(ReviewNotEligibleException::new);
        Instant now = clock.instant();
        Review review =
                Review.write(
                        eligibility,
                        command.buyerId(),
                        command.rating(),
                        command.content(),
                        command.fileIds(),
                        now);
        reviews.save(review);
        eligibilities.save(eligibility);

        RatingSummary summary = summaries.lockOrCreate(review.productId(), now);
        summary.add(review.rating(), now);
        summaries.save(summary);

        outbox.append(
                REVIEW_TOPIC, review.id().toString(), ReviewCreated.TYPE, ReviewCreated.of(review));
        outbox.append(
                RATING_SUMMARY_TOPIC,
                review.productId().toString(),
                RatingSummaryChanged.TYPE,
                RatingSummaryChanged.of(summary));
        return review.id();
    }
}
