package com.openmarket.review.command.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 판매자 답글과 관리자 숨김·보임(docs/architecture/review.md "답글", "숨김"). */
class ReviewModerationTest {

    static final Instant NOW = Instant.parse("2026-10-29T01:00:00Z");
    static final Instant LATER = Instant.parse("2026-10-30T01:00:00Z");

    static Review review() {
        ReviewEligibility eligibility = ReviewTest.eligibility();
        return Review.write(eligibility, ReviewTest.BUYER, 4, "사이즈가 잘 맞고 좋아요", List.of(), NOW);
    }

    @Test
    @DisplayName("숨기면 HIDDEN이고 다시 숨기면 바뀐 것이 없다")
    void hides() {
        Review review = review();

        assertThat(review.hide(LATER)).isTrue();
        assertThat(review.status()).isEqualTo(ReviewStatus.HIDDEN);
        assertThat(review.updatedAt()).isEqualTo(LATER);
        assertThat(review.hide(LATER.plusSeconds(1))).isFalse();
        assertThat(review.updatedAt()).isEqualTo(LATER);
    }

    @Test
    @DisplayName("숨긴 리뷰를 다시 보이면 VISIBLE이고 보이는 리뷰를 보이면 바뀐 것이 없다")
    void shows() {
        Review review = review();

        assertThat(review.show(LATER)).isFalse();
        review.hide(LATER);
        assertThat(review.show(LATER.plusSeconds(1))).isTrue();
        assertThat(review.status()).isEqualTo(ReviewStatus.VISIBLE);
    }

    @Test
    @DisplayName("숨김·보임 사유는 앞뒤 공백을 지우고 1~200자")
    void checksReason() {
        assertThat(ModerationReason.of("  욕설이 있습니다  ").value()).isEqualTo("욕설이 있습니다");
        assertThat(ModerationReason.of("가".repeat(200)).value()).hasSize(200);
        assertThatThrownBy(() -> ModerationReason.of("   "))
                .isInstanceOf(InvalidReviewException.class);
        assertThatThrownBy(() -> ModerationReason.of("가".repeat(201)))
                .isInstanceOf(InvalidReviewException.class);
    }

    @Test
    @DisplayName("리뷰 상품의 판매자가 답글을 단다. 본문은 앞뒤 공백을 지운다")
    void writesReply() {
        Review review = review();

        ReviewReply reply = ReviewReply.write(review, review.sellerId(), "  감사합니다  ", LATER);

        assertThat(reply.reviewId()).isEqualTo(review.id());
        assertThat(reply.sellerId()).isEqualTo(review.sellerId());
        assertThat(reply.content()).isEqualTo("감사합니다");
        assertThat(reply.createdAt()).isEqualTo(LATER);
        assertThat(reply.updatedAt()).isEqualTo(LATER);
    }

    @Test
    @DisplayName("다른 판매자의 리뷰에 답글을 달면 404 REVIEW_NOT_FOUND")
    void rejectsOtherSeller() {
        Review review = review();

        assertThatThrownBy(() -> ReviewReply.write(review, UUID.randomUUID(), "감사합니다", LATER))
                .isInstanceOf(ReviewNotFoundException.class);
    }

    @Test
    @DisplayName("숨긴 리뷰에도 답글을 단다")
    void repliesToHiddenReview() {
        Review review = review();
        review.hide(LATER);

        assertThat(ReviewReply.write(review, review.sellerId(), "확인했습니다", LATER).content())
                .isEqualTo("확인했습니다");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("답글은 1~1,000자")
    void checksReplyLength(String blank) {
        Review review = review();

        assertThatThrownBy(() -> ReviewReply.write(review, review.sellerId(), blank, LATER))
                .isInstanceOf(InvalidReviewException.class);
        assertThatThrownBy(
                        () -> ReviewReply.write(review, review.sellerId(), "가".repeat(1001), LATER))
                .isInstanceOf(InvalidReviewException.class);
        assertThat(ReviewReply.write(review, review.sellerId(), "가".repeat(1000), LATER).content())
                .hasSize(1000);
    }

    @Test
    @DisplayName("답글을 다시 쓰면 고친다. 같은 본문이면 바뀐 것이 없다")
    void editsReply() {
        Review review = review();
        ReviewReply reply = ReviewReply.write(review, review.sellerId(), "감사합니다", NOW);

        assertThat(reply.edit("  감사합니다  ", LATER)).isFalse();
        assertThat(reply.updatedAt()).isEqualTo(NOW);
        assertThat(reply.edit("다음에도 이용해 주세요", LATER)).isTrue();
        assertThat(reply.content()).isEqualTo("다음에도 이용해 주세요");
        assertThat(reply.createdAt()).isEqualTo(NOW);
        assertThat(reply.updatedAt()).isEqualTo(LATER);
        assertThatThrownBy(() -> reply.edit(" ", LATER)).isInstanceOf(InvalidReviewException.class);
    }
}
