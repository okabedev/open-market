package com.openmarket.promotion.query.dao;

import static com.openmarket.promotion.command.domain.QCoupon.coupon;

import com.openmarket.promotion.query.dto.CouponIssuer;
import com.openmarket.promotion.query.dto.CouponStatus;
import com.openmarket.promotion.query.dto.CouponView;
import com.openmarket.promotion.query.dto.DiscountType;
import com.openmarket.promotion.query.dto.PageView;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.Predicate;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

/** 쿠폰 정책 조회. */
@Repository
public class CouponQueryDao {

    /** 공개 목록 상한. 한 발행 주체가 동시에 발급하는 쿠폰은 이보다 훨씬 적다. */
    static final int MAX_ISSUABLE = 100;

    private static final Expression<?>[] COLUMNS = {
        coupon.id,
        coupon.issuer.stringValue(),
        coupon.sellerId,
        coupon.name,
        coupon.discountType.stringValue(),
        coupon.discountValue,
        coupon.maxDiscount,
        coupon.minOrderAmount,
        coupon.quantity,
        coupon.issueStartAt,
        coupon.issueEndAt,
        coupon.validDays,
        coupon.status.stringValue(),
        coupon.stoppedAt,
        coupon.createdAt
    };

    private final JPAQueryFactory query;

    public CouponQueryDao(JPAQueryFactory query) {
        this.query = query;
    }

    public Optional<CouponView> find(UUID couponId) {
        return Optional.ofNullable(
                        query.select(COLUMNS).from(coupon).where(coupon.id.eq(couponId)).fetchOne())
                .map(CouponQueryDao::toView);
    }

    /** 관리자·판매자 목록. 최근 생성 순. 조건이 null이면 거르지 않는다. */
    public PageView<CouponView> findPage(
            @Nullable CouponIssuer issuer,
            @Nullable UUID sellerId,
            @Nullable CouponStatus status,
            int page,
            int size) {
        BooleanBuilder where = new BooleanBuilder();
        if (issuer != null) {
            where.and(coupon.issuer.stringValue().eq(issuer.name()));
        }
        if (sellerId != null) {
            where.and(coupon.sellerId.eq(sellerId));
        }
        if (status != null) {
            where.and(coupon.status.stringValue().eq(status.name()));
        }
        long total =
                Objects.requireNonNullElse(
                        query.select(coupon.count()).from(coupon).where(where).fetchOne(), 0L);
        List<CouponView> items =
                query
                        .select(COLUMNS)
                        .from(coupon)
                        .where(where)
                        .orderBy(coupon.createdAt.desc(), coupon.id.desc())
                        .offset((long) page * size)
                        .limit(size)
                        .fetch()
                        .stream()
                        .map(CouponQueryDao::toView)
                        .toList();
        return new PageView<>(items, total, page, size);
    }

    /**
     * 지금 내려받을 수 있는 쿠폰. {@code sellerId}가 null이면 플랫폼 쿠폰, 아니면 그 판매자 쿠폰. 발급 기간이 먼저 끝나는 순. 규칙은 도메인의
     * {@code Coupon.isIssuableAt}과 같다.
     */
    public List<CouponView> findIssuable(@Nullable UUID sellerId, Instant now) {
        Predicate issuer =
                sellerId == null
                        ? coupon.issuer.stringValue().eq(CouponIssuer.PLATFORM.name())
                        : coupon.sellerId.eq(sellerId);
        return query
                .select(COLUMNS)
                .from(coupon)
                .where(
                        issuer,
                        coupon.status.stringValue().eq(CouponStatus.ISSUING.name()),
                        coupon.issueStartAt.loe(now),
                        coupon.issueEndAt.gt(now))
                .orderBy(coupon.issueEndAt.asc(), coupon.id.asc())
                .limit(MAX_ISSUABLE)
                .fetch()
                .stream()
                .map(CouponQueryDao::toView)
                .toList();
    }

    private static CouponView toView(Tuple row) {
        return new CouponView(
                Objects.requireNonNull(row.get(coupon.id)),
                CouponIssuer.valueOf(Objects.requireNonNull(row.get(1, String.class))),
                row.get(coupon.sellerId),
                Objects.requireNonNull(row.get(coupon.name)),
                DiscountType.valueOf(Objects.requireNonNull(row.get(4, String.class))),
                Objects.requireNonNull(row.get(coupon.discountValue)),
                row.get(coupon.maxDiscount),
                Objects.requireNonNull(row.get(coupon.minOrderAmount)),
                Objects.requireNonNull(row.get(coupon.quantity)),
                Objects.requireNonNull(row.get(coupon.issueStartAt)),
                Objects.requireNonNull(row.get(coupon.issueEndAt)),
                Objects.requireNonNull(row.get(coupon.validDays)),
                CouponStatus.valueOf(Objects.requireNonNull(row.get(12, String.class))),
                row.get(coupon.stoppedAt),
                Objects.requireNonNull(row.get(coupon.createdAt)));
    }
}
