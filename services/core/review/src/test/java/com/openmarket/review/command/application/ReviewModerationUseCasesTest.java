package com.openmarket.review.command.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmarket.common.core.audit.AuditEntry;
import com.openmarket.review.command.domain.InvalidReviewException;
import com.openmarket.review.command.domain.RatingSummaryChanged;
import com.openmarket.review.command.domain.ReviewNotFoundException;
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

/** 판매자 답글, 관리자 숨김·보임(docs/architecture/review.md "답글", "숨김"). */
class ReviewModerationUseCasesTest {

    static final Instant CONFIRMED_AT = Instant.parse("2026-10-28T07:00:00Z");
    static final Instant NOW = Instant.parse("2026-10-29T01:00:00Z");

    ReviewFakes.Eligibilities eligibilities;
    ReviewFakes.Reviews reviews;
    ReviewFakes.Replies replies;
    ReviewFakes.Summaries summaries;
    ReviewFakes.RecordingOutbox outbox;
    ReviewFakes.RecordingAuditLog auditLog;
    WriteReviewReplyUseCase reply;
    ChangeReviewVisibilityUseCase visibility;

    UUID sellerId = UUID.randomUUID();
    UUID buyerId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    UUID adminId = UUID.randomUUID();
    UUID four;
    UUID two;

    @BeforeEach
    void setUp() {
        eligibilities = new ReviewFakes.Eligibilities();
        reviews = new ReviewFakes.Reviews();
        replies = new ReviewFakes.Replies();
        summaries = new ReviewFakes.Summaries();
        outbox = new ReviewFakes.RecordingOutbox();
        auditLog = new ReviewFakes.RecordingAuditLog();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        reply = new WriteReviewReplyUseCase(reviews, replies, clock);
        visibility = new ChangeReviewVisibilityUseCase(reviews, summaries, outbox, auditLog, clock);

        CreateReviewEligibilitiesUseCase create =
                new CreateReviewEligibilitiesUseCase(eligibilities, clock);
        WriteReviewUseCase write =
                new WriteReviewUseCase(eligibilities, reviews, summaries, outbox, clock);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        create.execute(
                new CreateReviewEligibilitiesCommand(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        sellerId,
                        buyerId,
                        CONFIRMED_AT,
                        List.of(
                                new CreateReviewEligibilitiesCommand.Line(
                                        first, productId, "반팔 티셔츠", List.of()),
                                new CreateReviewEligibilitiesCommand.Line(
                                        second, productId, "반팔 티셔츠", List.of()))));
        four = write.execute(new WriteReviewCommand(buyerId, first, 4, "사이즈가 잘 맞고 좋아요", List.of()));
        two = write.execute(new WriteReviewCommand(buyerId, second, 2, "배송이 늦어서 아쉬워요", List.of()));
        outbox.events.clear();
    }

    @Test
    @DisplayName("판매자가 답글을 처음 쓰면 만들고, 다시 쓰면 고친다")
    void writesAndEditsReply() {
        reply.execute(new WriteReviewReplyCommand(sellerId, four, "감사합니다"));
        reply.execute(new WriteReviewReplyCommand(sellerId, four, "다음에도 이용해 주세요"));

        assertThat(replies.rows).containsOnlyKeys(four);
        assertThat(replies.rows.get(four).content()).isEqualTo("다음에도 이용해 주세요");
        assertThat(outbox.events).isEmpty();
    }

    @Test
    @DisplayName("같은 답글을 다시 쓰면 저장하지 않는다")
    void skipsSameReply() {
        reply.execute(new WriteReviewReplyCommand(sellerId, four, "감사합니다"));
        reply.execute(new WriteReviewReplyCommand(sellerId, four, "감사합니다"));

        assertThat(replies.saves).isEqualTo(1);
    }

    @Test
    @DisplayName("없는 리뷰나 다른 판매자의 리뷰면 404 REVIEW_NOT_FOUND")
    void rejectsReplyToOthers() {
        assertThatThrownBy(
                        () ->
                                reply.execute(
                                        new WriteReviewReplyCommand(
                                                sellerId, UUID.randomUUID(), "감사합니다")))
                .isInstanceOf(ReviewNotFoundException.class);
        assertThatThrownBy(
                        () ->
                                reply.execute(
                                        new WriteReviewReplyCommand(
                                                UUID.randomUUID(), four, "감사합니다")))
                .isInstanceOf(ReviewNotFoundException.class);
        assertThat(replies.rows).isEmpty();
    }

    @Test
    @DisplayName("숨기면 별점 요약에서 빼고 RatingSummaryChanged와 감사 로그 REVIEW_HIDDEN을 남긴다")
    void hides() {
        visibility.execute(new ChangeReviewVisibilityCommand(adminId, two, false, "  광고성 내용  "));

        assertThat(reviews.rows.get(two).status()).isEqualTo(ReviewStatus.HIDDEN);
        assertThat(outbox.types()).containsExactly(RatingSummaryChanged.TYPE);
        assertThat(outbox.events.getFirst().aggregateId()).isEqualTo(productId.toString());
        assertThat(outbox.events.getFirst().payload())
                .isEqualTo(new RatingSummaryChanged(productId, 1, 4, new BigDecimal("4.0"), 3));
        assertThat(auditLog.entries)
                .containsExactly(
                        new AuditEntry(
                                adminId.toString(),
                                "REVIEW_HIDDEN",
                                "REVIEW",
                                two.toString(),
                                "광고성 내용"));
    }

    @Test
    @DisplayName("다시 보이면 별점 요약에 더하고 RatingSummaryChanged와 감사 로그 REVIEW_SHOWN을 남긴다")
    void shows() {
        visibility.execute(new ChangeReviewVisibilityCommand(adminId, two, false, "광고성 내용"));
        outbox.events.clear();
        auditLog.entries.clear();

        visibility.execute(new ChangeReviewVisibilityCommand(adminId, two, true, "오판이었다"));

        assertThat(reviews.rows.get(two).status()).isEqualTo(ReviewStatus.VISIBLE);
        assertThat(outbox.events.getFirst().payload())
                .isEqualTo(new RatingSummaryChanged(productId, 2, 6, new BigDecimal("3.0"), 4));
        assertThat(auditLog.entries).extracting(AuditEntry::action).containsExactly("REVIEW_SHOWN");
    }

    @Test
    @DisplayName("같은 상태로 다시 하면 이벤트와 감사 로그가 없다")
    void ignoresSameState() {
        visibility.execute(new ChangeReviewVisibilityCommand(adminId, two, true, "이미 보인다"));
        visibility.execute(new ChangeReviewVisibilityCommand(adminId, two, false, "광고성 내용"));
        visibility.execute(new ChangeReviewVisibilityCommand(adminId, two, false, "광고성 내용"));

        assertThat(outbox.events).hasSize(1);
        assertThat(auditLog.entries).hasSize(1);
        assertThat(summaries.rows.get(productId).ratingCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("없는 리뷰면 404 REVIEW_NOT_FOUND, 사유가 틀리면 400 REVIEW_INVALID_INPUT")
    void rejectsHide() {
        assertThatThrownBy(
                        () ->
                                visibility.execute(
                                        new ChangeReviewVisibilityCommand(
                                                adminId, UUID.randomUUID(), false, "사유")))
                .isInstanceOf(ReviewNotFoundException.class);
        assertThatThrownBy(
                        () ->
                                visibility.execute(
                                        new ChangeReviewVisibilityCommand(
                                                adminId, two, false, " ")))
                .isInstanceOf(InvalidReviewException.class);
        assertThatThrownBy(
                        () ->
                                visibility.execute(
                                        new ChangeReviewVisibilityCommand(
                                                adminId, two, true, "가".repeat(201))))
                .isInstanceOf(InvalidReviewException.class);
        assertThat(reviews.rows.get(two).status()).isEqualTo(ReviewStatus.VISIBLE);
        assertThat(outbox.events).isEmpty();
    }
}
