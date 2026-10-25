package com.openmarket.promotion.query.dao;

import static com.openmarket.promotion.command.domain.QCoupon.coupon;
import static com.openmarket.promotion.command.domain.QIssuedCoupon.issuedCoupon;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;
import com.openmarket.promotion.query.dto.CouponIssuer;
import com.openmarket.promotion.query.dto.CursorPageView;
import com.openmarket.promotion.query.dto.DiscountType;
import com.openmarket.promotion.query.dto.IssuedCouponState;
import com.openmarket.promotion.query.dto.IssuedCouponView;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

/**
 * 구매자 내 쿠폰(docs/architecture/promotion.md "내 쿠폰"). 최근 발급 순 커서 목록이다(인덱스 {@code
 * issued_coupon_owner_idx}).
 */
@Repository
public class IssuedCouponQueryDao {

    private static final String AVAILABLE = "AVAILABLE";

    private static final Expression<?>[] COLUMNS = {
        issuedCoupon.id,
        issuedCoupon.couponId,
        coupon.issuer.stringValue(),
        coupon.sellerId,
        coupon.name,
        coupon.discountType.stringValue(),
        coupon.discountValue,
        coupon.maxDiscount,
        coupon.minOrderAmount,
        issuedCoupon.status.stringValue(),
        issuedCoupon.issuedAt,
        issuedCoupon.expiresAt
    };

    private final JPAQueryFactory query;

    public IssuedCouponQueryDao(JPAQueryFactory query) {
        this.query = query;
    }

    /**
     * 회원의 발급 쿠폰.
     *
     * @param usableOnly true면 지금 쓸 수 있는 쿠폰({@code AVAILABLE}이고 만료 시각 전)만
     * @param now 만료를 가르는 시각
     */
    public CursorPageView<IssuedCouponView> findMine(
            UUID memberId, boolean usableOnly, Instant now, @Nullable String cursor, int size) {
        BooleanBuilder base = new BooleanBuilder(issuedCoupon.memberId.eq(memberId));
        if (usableOnly) {
            base.and(issuedCoupon.status.stringValue().eq(AVAILABLE))
                    .and(issuedCoupon.expiresAt.gt(now));
        }
        BooleanBuilder where = new BooleanBuilder(base);
        if (cursor != null) {
            Cursor after = Cursor.decode(cursor);
            where.and(
                    issuedCoupon
                            .issuedAt
                            .lt(after.at())
                            .or(
                                    issuedCoupon
                                            .issuedAt
                                            .eq(after.at())
                                            .and(issuedCoupon.id.lt(after.id()))));
        }
        List<IssuedCouponView> rows =
                query
                        .select(COLUMNS)
                        .from(issuedCoupon)
                        .join(coupon)
                        .on(coupon.id.eq(issuedCoupon.couponId))
                        .where(where)
                        .orderBy(issuedCoupon.issuedAt.desc(), issuedCoupon.id.desc())
                        .limit(size + 1L)
                        .fetch()
                        .stream()
                        .map(row -> toView(row, now))
                        .toList();
        long total =
                Objects.requireNonNullElse(
                        query.select(issuedCoupon.count())
                                .from(issuedCoupon)
                                .where(base)
                                .fetchOne(),
                        0L);
        boolean hasNext = rows.size() > size;
        List<IssuedCouponView> items = hasNext ? rows.subList(0, size) : rows;
        String nextCursor =
                hasNext
                        ? Cursor.encode(
                                new Cursor(
                                        items.getLast().issuedAt(),
                                        items.getLast().issuedCouponId()))
                        : null;
        return new CursorPageView<>(List.copyOf(items), total, nextCursor);
    }

    private static IssuedCouponView toView(Tuple row, Instant now) {
        Instant expiresAt = Objects.requireNonNull(row.get(issuedCoupon.expiresAt));
        String status = Objects.requireNonNull(row.get(9, String.class));
        IssuedCouponState state =
                switch (status) {
                    case AVAILABLE ->
                            expiresAt.isAfter(now)
                                    ? IssuedCouponState.USABLE
                                    : IssuedCouponState.EXPIRED;
                    default -> IssuedCouponState.valueOf(status);
                };
        return new IssuedCouponView(
                Objects.requireNonNull(row.get(issuedCoupon.id)),
                Objects.requireNonNull(row.get(issuedCoupon.couponId)),
                CouponIssuer.valueOf(Objects.requireNonNull(row.get(2, String.class))),
                row.get(coupon.sellerId),
                Objects.requireNonNull(row.get(coupon.name)),
                DiscountType.valueOf(Objects.requireNonNull(row.get(5, String.class))),
                Objects.requireNonNull(row.get(coupon.discountValue)),
                row.get(coupon.maxDiscount),
                Objects.requireNonNull(row.get(coupon.minOrderAmount)),
                state,
                Objects.requireNonNull(row.get(issuedCoupon.issuedAt)),
                expiresAt);
    }

    /** 이전 쪽 마지막 행의 발급 시각과 ID. */
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
                    ErrorType.INVALID, "PROMOTION_INVALID_CURSOR", "쿠폰 목록 커서가 올바르지 않습니다.");
        }
    }
}
