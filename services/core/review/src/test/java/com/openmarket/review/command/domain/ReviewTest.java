package com.openmarket.review.command.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReviewTest {

    static final Instant CONFIRMED_AT = Instant.parse("2026-10-28T07:00:00Z");
    static final Instant NOW = Instant.parse("2026-10-29T01:00:00Z");
    static final UUID BUYER = UUID.randomUUID();

    static ReviewEligibility eligibility() {
        return ReviewEligibility.create(
                new ReviewEligibility.Purchase(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "반팔 티셔츠",
                        List.of("화이트", "M"),
                        UUID.randomUUID(),
                        BUYER,
                        CONFIRMED_AT),
                CONFIRMED_AT);
    }

    @Test
    @DisplayName("작성 자격의 주문 항목으로 리뷰를 쓰면 보이는 상태이고 자격에 리뷰 ID가 남는다")
    void writes() {
        ReviewEligibility eligibility = eligibility();
        UUID file = UUID.randomUUID();

        Review review =
                Review.write(eligibility, BUYER, 5, "  사이즈가 잘 맞고 좋아요  ", List.of(file), NOW);

        assertThat(review.status()).isEqualTo(ReviewStatus.VISIBLE);
        assertThat(review.orderLineId()).isEqualTo(eligibility.orderLineId());
        assertThat(review.productId()).isEqualTo(eligibility.productId());
        assertThat(review.productName()).isEqualTo("반팔 티셔츠");
        assertThat(review.optionValues()).containsExactly("화이트", "M");
        assertThat(review.sellerId()).isEqualTo(eligibility.sellerId());
        assertThat(review.buyerId()).isEqualTo(BUYER);
        assertThat(review.rating()).isEqualTo(5);
        assertThat(review.content()).isEqualTo("사이즈가 잘 맞고 좋아요");
        assertThat(review.fileIds()).containsExactly(file);
        assertThat(review.createdAt()).isEqualTo(NOW);
        assertThat(eligibility.reviewId()).isEqualTo(review.id());
        assertThat(eligibility.isWritten()).isTrue();
    }

    @Test
    @DisplayName("남의 작성 자격이면 404 REVIEW_NOT_ELIGIBLE")
    void rejectsOthersEligibility() {
        ReviewEligibility eligibility = eligibility();

        assertThatThrownBy(
                        () ->
                                Review.write(
                                        eligibility,
                                        UUID.randomUUID(),
                                        5,
                                        "사이즈가 잘 맞고 좋아요",
                                        List.of(),
                                        NOW))
                .isInstanceOf(ReviewNotEligibleException.class);
        assertThat(eligibility.isWritten()).isFalse();
    }

    @Test
    @DisplayName("이미 리뷰를 쓴 주문 항목이면 409 REVIEW_ALREADY_WRITTEN")
    void rejectsSecondReview() {
        ReviewEligibility eligibility = eligibility();
        Review.write(eligibility, BUYER, 5, "사이즈가 잘 맞고 좋아요", List.of(), NOW);

        assertThatThrownBy(() -> Review.write(eligibility, BUYER, 4, "다시 쓰는 리뷰입니다", List.of(), NOW))
                .isInstanceOf(ReviewAlreadyWrittenException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 6, -1})
    @DisplayName("별점은 1~5")
    void rejectsRatingOutOfRange(int rating) {
        assertThatThrownBy(
                        () ->
                                Review.write(
                                        eligibility(),
                                        BUYER,
                                        rating,
                                        "사이즈가 잘 맞고 좋아요",
                                        List.of(),
                                        NOW))
                .isInstanceOf(InvalidReviewException.class);
    }

    @Test
    @DisplayName("본문은 앞뒤 공백을 지우고 10~1,000자")
    void checksContentLength() {
        assertThatThrownBy(
                        () -> Review.write(eligibility(), BUYER, 5, "  아홉글자입니다다  ", List.of(), NOW))
                .isInstanceOf(InvalidReviewException.class);
        assertThatThrownBy(
                        () ->
                                Review.write(
                                        eligibility(), BUYER, 5, "가".repeat(1001), List.of(), NOW))
                .isInstanceOf(InvalidReviewException.class);

        assertThat(Review.write(eligibility(), BUYER, 5, "열글자입니다열글자!", List.of(), NOW).content())
                .hasSize(10);
        assertThat(
                        Review.write(eligibility(), BUYER, 5, "가".repeat(1000), List.of(), NOW)
                                .content())
                .hasSize(1000);
    }

    @Test
    @DisplayName("사진은 같은 사진 없이 5장까지")
    void checksFiles() {
        List<UUID> six = IntStream.range(0, 6).mapToObj(i -> UUID.randomUUID()).toList();
        UUID same = UUID.randomUUID();

        assertThatThrownBy(() -> Review.write(eligibility(), BUYER, 5, "사이즈가 잘 맞고 좋아요", six, NOW))
                .isInstanceOf(InvalidReviewException.class);
        assertThatThrownBy(
                        () ->
                                Review.write(
                                        eligibility(),
                                        BUYER,
                                        5,
                                        "사이즈가 잘 맞고 좋아요",
                                        List.of(same, same),
                                        NOW))
                .isInstanceOf(InvalidReviewException.class);
        assertThat(
                        Review.write(
                                        eligibility(),
                                        BUYER,
                                        5,
                                        "사이즈가 잘 맞고 좋아요",
                                        six.subList(0, 5),
                                        NOW)
                                .fileIds())
                .containsExactlyElementsOf(six.subList(0, 5));
    }

    @Test
    @DisplayName("입력이 틀리면 자격에 리뷰 ID를 남기지 않는다")
    void keepsEligibilityOnInvalidInput() {
        ReviewEligibility eligibility = eligibility();

        assertThatThrownBy(
                        () -> Review.write(eligibility, BUYER, 9, "사이즈가 잘 맞고 좋아요", List.of(), NOW))
                .isInstanceOf(InvalidReviewException.class);

        assertThat(eligibility.isWritten()).isFalse();
    }

    @Test
    @DisplayName("작성 자격은 옵션을 두 개까지 받는다")
    void limitsOptionValues() {
        assertThatThrownBy(
                        () ->
                                new ReviewEligibility.Purchase(
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        "반팔 티셔츠",
                                        List.of("a", "b", "c"),
                                        UUID.randomUUID(),
                                        BUYER,
                                        CONFIRMED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
