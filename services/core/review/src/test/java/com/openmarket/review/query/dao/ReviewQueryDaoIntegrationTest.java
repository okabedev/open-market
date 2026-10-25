package com.openmarket.review.query.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.test.container.PostgresContainerConfiguration;
import com.openmarket.review.command.application.ChangeReviewVisibilityCommand;
import com.openmarket.review.command.application.ChangeReviewVisibilityUseCase;
import com.openmarket.review.command.application.CreateReviewEligibilitiesCommand;
import com.openmarket.review.command.application.CreateReviewEligibilitiesUseCase;
import com.openmarket.review.command.application.WriteReviewCommand;
import com.openmarket.review.command.application.WriteReviewReplyCommand;
import com.openmarket.review.command.application.WriteReviewReplyUseCase;
import com.openmarket.review.command.application.WriteReviewUseCase;
import com.openmarket.review.query.dto.CursorPageView;
import com.openmarket.review.query.dto.PageView;
import com.openmarket.review.query.dto.ProductReviewView;
import com.openmarket.review.query.dto.ReviewStatus;
import com.openmarket.review.query.dto.ReviewView;
import com.openmarket.review.query.dto.WritableReviewView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** 리뷰 목록 조회(docs/architecture/review.md "API"). */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@Import(PostgresContainerConfiguration.class)
class ReviewQueryDaoIntegrationTest {

    static final Instant CONFIRMED_AT = Instant.parse("2026-10-28T07:00:00Z");

    @Autowired ReviewQueryDao dao;
    @Autowired JdbcClient jdbc;
    @Autowired CreateReviewEligibilitiesUseCase createEligibilities;
    @Autowired WriteReviewUseCase write;
    @Autowired WriteReviewReplyUseCase reply;
    @Autowired ChangeReviewVisibilityUseCase visibility;

    final UUID buyerId = UUID.randomUUID();
    final UUID otherBuyerId = UUID.randomUUID();
    final UUID sellerId = UUID.randomUUID();
    final UUID otherSellerId = UUID.randomUUID();
    final UUID productId = UUID.randomUUID();
    final UUID otherProductId = UUID.randomUUID();
    final UUID fileA = UUID.randomUUID();
    final UUID fileB = UUID.randomUUID();

    UUID oldest;
    UUID hidden;
    UUID newest;
    UUID others;
    UUID writableOld;
    UUID writableNew;

    @BeforeEach
    void setUp() {
        UUID l1 = eligible(sellerId, buyerId, productId, List.of("화이트", "M"), CONFIRMED_AT);
        UUID l2 = eligible(sellerId, buyerId, productId, List.of(), CONFIRMED_AT);
        UUID l3 = eligible(sellerId, otherBuyerId, productId, List.of(), CONFIRMED_AT);
        UUID l4 = eligible(otherSellerId, buyerId, otherProductId, List.of(), CONFIRMED_AT);
        writableOld = eligible(sellerId, buyerId, productId, List.of("블랙"), CONFIRMED_AT);
        writableNew =
                eligible(sellerId, buyerId, productId, List.of(), CONFIRMED_AT.plusSeconds(60));

        oldest = write(l1, 5, List.of(fileB, fileA), "2026-10-29T01:00:00Z");
        hidden = write(l2, 1, List.of(), "2026-10-29T02:00:00Z");
        newest = write(l3, 4, List.of(), "2026-10-29T03:00:00Z");
        others = write(l4, 3, List.of(), "2026-10-29T04:00:00Z");

        reply.execute(new WriteReviewReplyCommand(sellerId, oldest, "구매해 주셔서 감사합니다"));
        visibility.execute(
                new ChangeReviewVisibilityCommand(UUID.randomUUID(), hidden, false, "광고성 내용"));
    }

    @Test
    @DisplayName("상품 리뷰는 보이는 리뷰만 최근 순이고 커서로 이어 받는다. 답글과 사진 순서가 함께 온다")
    void productReviews() {
        CursorPageView<ProductReviewView> first = dao.findProductReviews(productId, null, 1);

        assertThat(first.totalCount()).isEqualTo(2);
        assertThat(first.items()).extracting(ProductReviewView::reviewId).containsExactly(newest);
        assertThat(first.items().getFirst().reply()).isNull();
        assertThat(first.nextCursor()).isNotNull();

        CursorPageView<ProductReviewView> second =
                dao.findProductReviews(productId, first.nextCursor(), 1);

        assertThat(second.items()).extracting(ProductReviewView::reviewId).containsExactly(oldest);
        ProductReviewView view = second.items().getFirst();
        assertThat(view.optionValues()).containsExactly("화이트", "M");
        assertThat(view.rating()).isEqualTo(5);
        assertThat(view.content()).isEqualTo("사이즈가 잘 맞고 좋아요");
        assertThat(view.fileIds()).containsExactly(fileB, fileA);
        assertThat(view.reply()).isNotNull();
        assertThat(view.reply().content()).isEqualTo("구매해 주셔서 감사합니다");
        assertThat(view.createdAt()).isEqualTo(Instant.parse("2026-10-29T01:00:00Z"));
        assertThat(second.nextCursor()).isNull();
    }

    @Test
    @DisplayName("내 리뷰는 숨긴 리뷰도 상태와 함께 최근 순으로 보인다")
    void buyerReviews() {
        CursorPageView<ReviewView> page = dao.findBuyerReviews(buyerId, null, 10);

        assertThat(page.totalCount()).isEqualTo(3);
        assertThat(page.items())
                .extracting(ReviewView::reviewId)
                .containsExactly(others, hidden, oldest);
        assertThat(page.items().get(1).status()).isEqualTo(ReviewStatus.HIDDEN);
        ReviewView first = page.items().get(2);
        assertThat(first.productId()).isEqualTo(productId);
        assertThat(first.productName()).isEqualTo("반팔 티셔츠");
        assertThat(first.sellerId()).isEqualTo(sellerId);
        assertThat(first.buyerId()).isEqualTo(buyerId);
        assertThat(first.status()).isEqualTo(ReviewStatus.VISIBLE);
        assertThat(first.fileIds()).containsExactly(fileB, fileA);
        assertThat(first.reply()).isNotNull();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    @DisplayName("쓸 수 있는 리뷰는 리뷰를 쓰지 않은 내 작성 자격이고 구매확정 최근 순이다")
    void writableReviews() {
        CursorPageView<WritableReviewView> first = dao.findWritable(buyerId, null, 1);

        assertThat(first.totalCount()).isEqualTo(2);
        assertThat(first.items())
                .extracting(WritableReviewView::orderLineId)
                .containsExactly(writableNew);

        CursorPageView<WritableReviewView> second =
                dao.findWritable(buyerId, first.nextCursor(), 1);

        assertThat(second.items()).hasSize(1);
        WritableReviewView view = second.items().getFirst();
        assertThat(view.orderLineId()).isEqualTo(writableOld);
        assertThat(view.productId()).isEqualTo(productId);
        assertThat(view.productName()).isEqualTo("반팔 티셔츠");
        assertThat(view.optionValues()).containsExactly("블랙");
        assertThat(view.confirmedAt()).isEqualTo(CONFIRMED_AT);
        assertThat(second.nextCursor()).isNull();
        assertThat(dao.findWritable(otherBuyerId, null, 10).items()).isEmpty();
    }

    @Test
    @DisplayName("판매자 리뷰는 자기 상품 리뷰만, 숨긴 리뷰도 최근 순으로 보인다")
    void sellerReviews() {
        CursorPageView<ReviewView> page = dao.findSellerReviews(sellerId, null, 10);

        assertThat(page.totalCount()).isEqualTo(3);
        assertThat(page.items())
                .extracting(ReviewView::reviewId)
                .containsExactly(newest, hidden, oldest);
    }

    @Test
    @DisplayName("관리자 리뷰는 상태·상품·판매자로 거르는 쪽 번호 목록이다")
    void adminReviews() {
        PageView<ReviewView> hiddenOnly =
                dao.findAdminPage(ReviewStatus.HIDDEN, productId, null, 0, 20);
        assertThat(hiddenOnly.totalCount()).isEqualTo(1);
        assertThat(hiddenOnly.items()).extracting(ReviewView::reviewId).containsExactly(hidden);

        PageView<ReviewView> bySeller = dao.findAdminPage(null, null, sellerId, 1, 2);
        assertThat(bySeller.totalCount()).isEqualTo(3);
        assertThat(bySeller.page()).isEqualTo(1);
        assertThat(bySeller.items()).extracting(ReviewView::reviewId).containsExactly(oldest);

        PageView<ReviewView> byProduct =
                dao.findAdminPage(ReviewStatus.VISIBLE, otherProductId, null, 0, 20);
        assertThat(byProduct.items()).extracting(ReviewView::reviewId).containsExactly(others);
    }

    @Test
    @DisplayName("형식이 틀린 커서는 400 REVIEW_INVALID_CURSOR")
    void rejectsInvalidCursor() {
        assertThatThrownBy(() -> dao.findProductReviews(productId, "not-a-cursor", 10))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("REVIEW_INVALID_CURSOR");
        assertThatThrownBy(() -> dao.findWritable(buyerId, "x".repeat(300), 10))
                .isInstanceOf(BusinessException.class);
    }

    private UUID eligible(
            UUID seller, UUID buyer, UUID product, List<String> options, Instant confirmedAt) {
        UUID line = UUID.randomUUID();
        createEligibilities.execute(
                new CreateReviewEligibilitiesCommand(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        seller,
                        buyer,
                        confirmedAt,
                        List.of(
                                new CreateReviewEligibilitiesCommand.Line(
                                        line, product, "반팔 티셔츠", options))));
        return line;
    }

    private UUID write(UUID line, int rating, List<UUID> files, String createdAt) {
        UUID buyer =
                jdbc.sql("SELECT buyer_id FROM review_eligibility WHERE order_line_id = ?")
                        .param(line)
                        .query(UUID.class)
                        .single();
        UUID reviewId =
                write.execute(new WriteReviewCommand(buyer, line, rating, "사이즈가 잘 맞고 좋아요", files));
        // 목록 순서를 고정하려고 작성 시각을 정한다
        jdbc.sql("UPDATE review SET created_at = ? WHERE id = ?")
                .param(java.sql.Timestamp.from(Instant.parse(createdAt)))
                .param(reviewId)
                .update();
        return reviewId;
    }
}
