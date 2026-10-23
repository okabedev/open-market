package com.openmarket.review.command.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmarket.review.command.domain.RatingSummaryChanged;
import com.openmarket.review.command.domain.ReviewAlreadyWrittenException;
import com.openmarket.review.command.domain.ReviewCreated;
import com.openmarket.review.command.domain.ReviewEligibility;
import com.openmarket.review.command.domain.ReviewNotEligibleException;
import com.openmarket.review.command.domain.ReviewStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReviewUseCasesTest {

    static final Instant CONFIRMED_AT = Instant.parse("2026-10-28T07:00:00Z");
    static final Instant NOW = Instant.parse("2026-10-29T01:00:00Z");

    ReviewFakes.Eligibilities eligibilities;
    ReviewFakes.Reviews reviews;
    ReviewFakes.Summaries summaries;
    ReviewFakes.RecordingOutbox outbox;
    CreateReviewEligibilitiesUseCase createEligibilities;
    WriteReviewUseCase write;

    UUID orderId = UUID.randomUUID();
    UUID sellerOrderId = UUID.randomUUID();
    UUID sellerId = UUID.randomUUID();
    UUID buyerId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        eligibilities = new ReviewFakes.Eligibilities();
        reviews = new ReviewFakes.Reviews();
        summaries = new ReviewFakes.Summaries();
        outbox = new ReviewFakes.RecordingOutbox();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        createEligibilities = new CreateReviewEligibilitiesUseCase(eligibilities, clock);
        write = new WriteReviewUseCase(eligibilities, reviews, summaries, outbox, clock);
    }

    @Test
    @DisplayName("구매확정의 주문 항목마다 작성 자격을 만든다. 같은 항목이 다시 오면 넘긴다")
    void createsEligibilities() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        createEligibilities.execute(
                confirmed(line(first, List.of("화이트", "M")), line(second, List.of())));
        createEligibilities.execute(confirmed(line(first, List.of("블랙", "L"))));

        assertThat(eligibilities.rows).containsOnlyKeys(first, second);
        ReviewEligibility eligibility = eligibilities.rows.get(first);
        assertThat(eligibility.orderId()).isEqualTo(orderId);
        assertThat(eligibility.sellerOrderId()).isEqualTo(sellerOrderId);
        assertThat(eligibility.sellerId()).isEqualTo(sellerId);
        assertThat(eligibility.buyerId()).isEqualTo(buyerId);
        assertThat(eligibility.productId()).isEqualTo(productId);
        assertThat(eligibility.productName()).isEqualTo("반팔 티셔츠");
        assertThat(eligibility.optionValues()).containsExactly("화이트", "M");
        assertThat(eligibility.confirmedAt()).isEqualTo(CONFIRMED_AT);
        assertThat(eligibility.isWritten()).isFalse();
        assertThat(eligibilities.rows.get(second).optionValues()).isEmpty();
    }

    @Test
    @DisplayName("리뷰를 쓰면 저장하고, 별점 요약을 바꾸고, ReviewCreated와 RatingSummaryChanged를 낸다")
    void writes() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        createEligibilities.execute(confirmed(line(first, List.of()), line(second, List.of())));
        UUID file = UUID.randomUUID();

        UUID reviewId =
                write.execute(
                        new WriteReviewCommand(buyerId, first, 5, "사이즈가 잘 맞고 좋아요", List.of(file)));
        write.execute(new WriteReviewCommand(buyerId, second, 4, "배송이 빠르고 좋아요", List.of()));

        assertThat(reviews.rows).hasSize(2);
        assertThat(reviews.rows.get(reviewId).status()).isEqualTo(ReviewStatus.VISIBLE);
        assertThat(eligibilities.rows.get(first).reviewId()).isEqualTo(reviewId);
        assertThat(outbox.types())
                .containsExactly(
                        ReviewCreated.TYPE,
                        RatingSummaryChanged.TYPE,
                        ReviewCreated.TYPE,
                        RatingSummaryChanged.TYPE);

        ReviewFakes.RecordingOutbox.Appended created = outbox.events.get(0);
        assertThat(created.aggregateType()).isEqualTo("review.review");
        assertThat(created.aggregateId()).isEqualTo(reviewId.toString());
        assertThat(created.payload())
                .isEqualTo(
                        new ReviewCreated(
                                reviewId,
                                first,
                                productId,
                                "반팔 티셔츠",
                                sellerId,
                                buyerId,
                                5,
                                List.of(file),
                                NOW));

        ReviewFakes.RecordingOutbox.Appended changed = outbox.events.get(3);
        assertThat(changed.aggregateType()).isEqualTo("review.rating-summary");
        assertThat(changed.aggregateId()).isEqualTo(productId.toString());
        assertThat(changed.payload())
                .isEqualTo(new RatingSummaryChanged(productId, 2, 9, new BigDecimal("4.5"), 2));
    }

    @Test
    @DisplayName("작성 자격이 없거나 남의 것이면 404 REVIEW_NOT_ELIGIBLE이고 이벤트를 내지 않는다")
    void rejectsWithoutEligibility() {
        UUID line = UUID.randomUUID();
        createEligibilities.execute(confirmed(line(line, List.of())));

        assertThatThrownBy(
                        () ->
                                write.execute(
                                        new WriteReviewCommand(
                                                buyerId,
                                                UUID.randomUUID(),
                                                5,
                                                "사이즈가 잘 맞고 좋아요",
                                                List.of())))
                .isInstanceOf(ReviewNotEligibleException.class);
        assertThatThrownBy(
                        () ->
                                write.execute(
                                        new WriteReviewCommand(
                                                UUID.randomUUID(),
                                                line,
                                                5,
                                                "사이즈가 잘 맞고 좋아요",
                                                List.of())))
                .isInstanceOf(ReviewNotEligibleException.class);
        assertThat(outbox.events).isEmpty();
        assertThat(summaries.rows).isEmpty();
    }

    @Test
    @DisplayName("이미 쓴 주문 항목이면 409 REVIEW_ALREADY_WRITTEN이고 별점 요약은 그대로다")
    void rejectsSecondReview() {
        UUID line = UUID.randomUUID();
        createEligibilities.execute(confirmed(line(line, List.of())));
        write.execute(new WriteReviewCommand(buyerId, line, 5, "사이즈가 잘 맞고 좋아요", List.of()));

        assertThatThrownBy(
                        () ->
                                write.execute(
                                        new WriteReviewCommand(
                                                buyerId, line, 1, "다시 쓰는 리뷰입니다", List.of())))
                .isInstanceOf(ReviewAlreadyWrittenException.class);
        assertThat(summaries.rows.get(productId).ratingCount()).isEqualTo(1);
        assertThat(outbox.events).hasSize(2);
    }

    private CreateReviewEligibilitiesCommand confirmed(
            CreateReviewEligibilitiesCommand.Line... lines) {
        return new CreateReviewEligibilitiesCommand(
                orderId, sellerOrderId, sellerId, buyerId, CONFIRMED_AT, List.of(lines));
    }

    private CreateReviewEligibilitiesCommand.Line line(UUID orderLineId, List<String> options) {
        return new CreateReviewEligibilitiesCommand.Line(orderLineId, productId, "반팔 티셔츠", options);
    }
}
