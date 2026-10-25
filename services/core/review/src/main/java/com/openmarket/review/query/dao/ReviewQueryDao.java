package com.openmarket.review.query.dao;

import static com.openmarket.review.command.domain.QReview.review;
import static com.openmarket.review.command.domain.QReviewEligibility.reviewEligibility;
import static com.openmarket.review.command.domain.QReviewReply.reviewReply;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;
import com.openmarket.review.command.domain.QReviewFile;
import com.openmarket.review.query.dto.CursorPageView;
import com.openmarket.review.query.dto.PageView;
import com.openmarket.review.query.dto.ProductReviewView;
import com.openmarket.review.query.dto.ReviewReplyView;
import com.openmarket.review.query.dto.ReviewStatus;
import com.openmarket.review.query.dto.ReviewView;
import com.openmarket.review.query.dto.WritableReviewView;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.ComparablePath;
import com.querydsl.core.types.dsl.DateTimePath;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

/**
 * 리뷰 목록 조회(docs/architecture/review.md "API"). 목록은 최근 순이고 시각이 같으면 ID 역순이다. 답글은 같은 쿼리에서 붙이고, 사진은 그
 * 쪽의 리뷰 ID로 한 번 더 읽는다. 인덱스는 {@code V1__review.sql}, {@code V2__review_reply.sql}에 있다.
 */
@Repository
public class ReviewQueryDao {

    /**
     * 보이는 리뷰. 부분 인덱스 {@code review_product_idx}(조건 {@code status = 'VISIBLE'})를 쓰려고 바인딩 값이 아니라 SQL
     * 상수로 비교한다. 바인딩 값이면 일반 실행 계획에서 부분 인덱스를 쓰지 못한다.
     */
    private static final BooleanExpression VISIBLE =
            Expressions.booleanTemplate("{0} = 'VISIBLE'", review.status);

    private static final QReviewFile reviewFile = new QReviewFile("reviewFile");

    private static final Expression<?>[] COLUMNS = {
        review.id,
        review.orderLineId,
        review.productId,
        review.productName,
        review.optionValue1,
        review.optionValue2,
        review.sellerId,
        review.buyerId,
        review.rating,
        review.content,
        review.status.stringValue(),
        review.createdAt,
        reviewReply.content,
        reviewReply.createdAt,
        reviewReply.updatedAt
    };

    private final JPAQueryFactory query;

    public ReviewQueryDao(JPAQueryFactory query) {
        this.query = query;
    }

    /** 상품의 보이는 리뷰(공개). 인덱스 {@code review_product_idx}. */
    public CursorPageView<ProductReviewView> findProductReviews(
            UUID productId, @Nullable String cursor, int size) {
        CursorPageView<ReviewView> page =
                reviewPage(review.productId.eq(productId).and(VISIBLE), cursor, size);
        return new CursorPageView<>(
                page.items().stream()
                        .map(
                                v ->
                                        new ProductReviewView(
                                                v.reviewId(),
                                                v.optionValues(),
                                                v.rating(),
                                                v.content(),
                                                v.fileIds(),
                                                v.reply(),
                                                v.createdAt()))
                        .toList(),
                page.totalCount(),
                page.nextCursor());
    }

    /** 구매자의 리뷰. 숨긴 리뷰도 보인다. 인덱스 {@code review_buyer_idx}. */
    public CursorPageView<ReviewView> findBuyerReviews(
            UUID buyerId, @Nullable String cursor, int size) {
        return reviewPage(review.buyerId.eq(buyerId), cursor, size);
    }

    /** 판매자 상품의 리뷰. 숨긴 리뷰도 보인다. 인덱스 {@code review_seller_idx}. */
    public CursorPageView<ReviewView> findSellerReviews(
            UUID sellerId, @Nullable String cursor, int size) {
        return reviewPage(review.sellerId.eq(sellerId), cursor, size);
    }

    /** 관리자 목록. 조건이 null이면 거르지 않는다. 인덱스 {@code review_created_idx}. */
    public PageView<ReviewView> findAdminPage(
            @Nullable ReviewStatus status,
            @Nullable UUID productId,
            @Nullable UUID sellerId,
            int page,
            int size) {
        BooleanBuilder where = new BooleanBuilder();
        if (status != null) {
            where.and(review.status.stringValue().eq(status.name()));
        }
        if (productId != null) {
            where.and(review.productId.eq(productId));
        }
        if (sellerId != null) {
            where.and(review.sellerId.eq(sellerId));
        }
        List<Tuple> rows =
                selectReviews()
                        .where(where)
                        .orderBy(review.createdAt.desc(), review.id.desc())
                        .offset((long) page * size)
                        .limit(size)
                        .fetch();
        return new PageView<>(toViews(rows), count(where), page, size);
    }

    /** 구매자가 아직 쓰지 않은 작성 자격. 구매확정 최근 순. 인덱스 {@code review_eligibility_buyer_idx}. */
    public CursorPageView<WritableReviewView> findWritable(
            UUID buyerId, @Nullable String cursor, int size) {
        BooleanExpression base =
                reviewEligibility.buyerId.eq(buyerId).and(reviewEligibility.reviewId.isNull());
        BooleanBuilder where = new BooleanBuilder(base);
        if (cursor != null) {
            where.and(after(reviewEligibility.confirmedAt, reviewEligibility.orderLineId, cursor));
        }
        List<WritableReviewView> rows =
                query
                        .select(
                                reviewEligibility.orderLineId,
                                reviewEligibility.orderId,
                                reviewEligibility.productId,
                                reviewEligibility.productName,
                                reviewEligibility.optionValue1,
                                reviewEligibility.optionValue2,
                                reviewEligibility.confirmedAt)
                        .from(reviewEligibility)
                        .where(where)
                        .orderBy(
                                reviewEligibility.confirmedAt.desc(),
                                reviewEligibility.orderLineId.desc())
                        .limit(size + 1L)
                        .fetch()
                        .stream()
                        .map(
                                t ->
                                        new WritableReviewView(
                                                Objects.requireNonNull(
                                                        t.get(reviewEligibility.orderLineId)),
                                                Objects.requireNonNull(
                                                        t.get(reviewEligibility.orderId)),
                                                Objects.requireNonNull(
                                                        t.get(reviewEligibility.productId)),
                                                Objects.requireNonNull(
                                                        t.get(reviewEligibility.productName)),
                                                options(
                                                        t.get(reviewEligibility.optionValue1),
                                                        t.get(reviewEligibility.optionValue2)),
                                                Objects.requireNonNull(
                                                        t.get(reviewEligibility.confirmedAt))))
                        .toList();
        Long total =
                query.select(reviewEligibility.count())
                        .from(reviewEligibility)
                        .where(base)
                        .fetchOne();
        return cursorPage(
                rows,
                size,
                total == null ? 0 : total,
                v -> new Cursor(v.confirmedAt(), v.orderLineId()));
    }

    private CursorPageView<ReviewView> reviewPage(
            BooleanExpression base, @Nullable String cursor, int size) {
        BooleanBuilder where = new BooleanBuilder(base);
        if (cursor != null) {
            where.and(after(review.createdAt, review.id, cursor));
        }
        List<Tuple> rows =
                selectReviews()
                        .where(where)
                        .orderBy(review.createdAt.desc(), review.id.desc())
                        .limit(size + 1L)
                        .fetch();
        return cursorPage(
                toViews(rows), size, count(base), v -> new Cursor(v.createdAt(), v.reviewId()));
    }

    private JPAQuery<Tuple> selectReviews() {
        return query.select(COLUMNS)
                .from(review)
                .leftJoin(reviewReply)
                .on(reviewReply.reviewId.eq(review.id));
    }

    private long count(Predicate where) {
        Long total = query.select(review.count()).from(review).where(where).fetchOne();
        return total == null ? 0 : total;
    }

    private List<ReviewView> toViews(List<Tuple> rows) {
        Map<UUID, List<UUID>> files =
                filesOf(rows.stream().map(t -> Objects.requireNonNull(t.get(review.id))).toList());
        return rows.stream()
                .map(
                        t -> {
                            UUID id = Objects.requireNonNull(t.get(review.id));
                            String replyContent = t.get(reviewReply.content);
                            ReviewReplyView reply =
                                    replyContent == null
                                            ? null
                                            : new ReviewReplyView(
                                                    replyContent,
                                                    Objects.requireNonNull(
                                                            t.get(reviewReply.createdAt)),
                                                    Objects.requireNonNull(
                                                            t.get(reviewReply.updatedAt)));
                            return new ReviewView(
                                    id,
                                    Objects.requireNonNull(t.get(review.orderLineId)),
                                    Objects.requireNonNull(t.get(review.productId)),
                                    Objects.requireNonNull(t.get(review.productName)),
                                    options(t.get(review.optionValue1), t.get(review.optionValue2)),
                                    Objects.requireNonNull(t.get(review.sellerId)),
                                    Objects.requireNonNull(t.get(review.buyerId)),
                                    Objects.requireNonNull(t.get(review.rating)),
                                    Objects.requireNonNull(t.get(review.content)),
                                    files.getOrDefault(id, List.of()),
                                    ReviewStatus.valueOf(
                                            Objects.requireNonNull(t.get(10, String.class))),
                                    reply,
                                    Objects.requireNonNull(t.get(review.createdAt)));
                        })
                .toList();
    }

    /** 리뷰마다 사진 ID를 넣은 순서대로. */
    private Map<UUID, List<UUID>> filesOf(List<UUID> reviewIds) {
        Map<UUID, List<UUID>> files = new LinkedHashMap<>();
        if (reviewIds.isEmpty()) {
            return files;
        }
        for (Tuple t :
                query.select(review.id, reviewFile.fileId)
                        .from(review)
                        .join(review.files, reviewFile)
                        .where(review.id.in(reviewIds))
                        .orderBy(review.id.asc(), reviewFile.position.asc())
                        .fetch()) {
            files.computeIfAbsent(Objects.requireNonNull(t.get(review.id)), k -> new ArrayList<>())
                    .add(Objects.requireNonNull(t.get(reviewFile.fileId)));
        }
        return files;
    }

    private static List<String> options(@Nullable String first, @Nullable String second) {
        List<String> values = new ArrayList<>(2);
        if (first != null) {
            values.add(first);
        }
        if (second != null) {
            values.add(second);
        }
        return List.copyOf(values);
    }

    private static BooleanExpression after(
            DateTimePath<Instant> at, ComparablePath<UUID> id, String cursor) {
        Cursor after = Cursor.decode(cursor);
        return at.lt(after.at()).or(at.eq(after.at()).and(id.lt(after.id())));
    }

    private static <T> CursorPageView<T> cursorPage(
            List<T> rows, int size, long total, Function<T, Cursor> cursorOf) {
        boolean hasNext = rows.size() > size;
        List<T> items = hasNext ? rows.subList(0, size) : rows;
        String nextCursor = hasNext ? Cursor.encode(cursorOf.apply(items.getLast())) : null;
        return new CursorPageView<>(List.copyOf(items), total, nextCursor);
    }

    /** 이전 쪽 마지막 행의 시각과 ID. */
    record Cursor(Instant at, UUID id) {

        private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
        private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
        private static final int MAX_LENGTH = 200;

        static String encode(Cursor cursor) {
            return ENCODER.encodeToString(
                    (cursor.at() + "|" + cursor.id()).getBytes(StandardCharsets.UTF_8));
        }

        static Cursor decode(String cursor) {
            if (cursor.length() > MAX_LENGTH) {
                throw invalid();
            }
            try {
                String text = new String(DECODER.decode(cursor), StandardCharsets.UTF_8);
                int separator = text.indexOf('|');
                if (separator < 0) {
                    throw invalid();
                }
                return new Cursor(
                        Instant.parse(text.substring(0, separator)),
                        UUID.fromString(text.substring(separator + 1)));
            } catch (IllegalArgumentException | DateTimeParseException e) {
                throw invalid();
            }
        }

        private static BusinessException invalid() {
            return new BusinessException(
                    ErrorType.INVALID, "REVIEW_INVALID_CURSOR", "리뷰 목록 커서가 올바르지 않습니다.");
        }
    }
}
