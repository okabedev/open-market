package com.openmarket.settlement.query.dao;

import static com.openmarket.settlement.command.domain.QSettlement.settlement;
import static com.openmarket.settlement.command.domain.QSettlementItem.settlementItem;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;
import com.openmarket.settlement.command.domain.QSettlementItemLine;
import com.openmarket.settlement.query.dto.CursorPageView;
import com.openmarket.settlement.query.dto.PageView;
import com.openmarket.settlement.query.dto.PendingSettlementSummaryView;
import com.openmarket.settlement.query.dto.SettlementItemLineView;
import com.openmarket.settlement.query.dto.SettlementItemView;
import com.openmarket.settlement.query.dto.SettlementStatus;
import com.openmarket.settlement.query.dto.SettlementView;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.ComparablePath;
import com.querydsl.core.types.dsl.DateTimePath;
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
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

/**
 * 정산·정산 대상 조회(docs/architecture/settlement.md "API"). 정산 목록은 기간 최근 순, 대상 목록은 구매확정 최근 순이고 시각이 같으면 ID
 * 역순이다. 대상의 주문 항목은 그 쪽의 대상 ID로 한 번 더 읽는다. 인덱스는 {@code V1__settlement.sql}, {@code
 * V2__settlement.sql}에 있다.
 */
@Repository
public class SettlementQueryDao {

    private static final QSettlementItemLine line = new QSettlementItemLine("line");

    private static final Expression<?>[] SETTLEMENT_COLUMNS = {
        settlement.id,
        settlement.sellerId,
        settlement.periodStart,
        settlement.periodEnd,
        settlement.itemCount,
        settlement.salesAmount,
        settlement.commissionAmount,
        settlement.sellerCouponDiscount,
        settlement.amount,
        settlement.status.stringValue(),
        settlement.confirmedAt,
        settlement.paidOutAt
    };

    private static final Expression<?>[] ITEM_COLUMNS = {
        settlementItem.id,
        settlementItem.sellerOrderId,
        settlementItem.orderId,
        settlementItem.confirmedAt,
        settlementItem.periodStart,
        settlementItem.periodEnd,
        settlementItem.salesAmount,
        settlementItem.commissionAmount,
        settlementItem.sellerCouponDiscount,
        settlementItem.settlementAmount
    };

    private final JPAQueryFactory query;

    public SettlementQueryDao(JPAQueryFactory query) {
        this.query = query;
    }

    public Optional<SettlementView> findSettlement(UUID settlementId) {
        return Optional.ofNullable(
                        query.select(SETTLEMENT_COLUMNS)
                                .from(settlement)
                                .where(settlement.id.eq(settlementId))
                                .fetchOne())
                .map(SettlementQueryDao::toSettlement);
    }

    /** 판매자의 정산. 기간 최근 순. 인덱스 {@code settlement_seller_period_key}. */
    public CursorPageView<SettlementView> findSellerSettlements(
            UUID sellerId, @Nullable String cursor, int size) {
        BooleanExpression base = settlement.sellerId.eq(sellerId);
        BooleanBuilder where = new BooleanBuilder(base);
        if (cursor != null) {
            where.and(after(settlement.periodStart, settlement.id, cursor));
        }
        List<SettlementView> rows =
                query
                        .select(SETTLEMENT_COLUMNS)
                        .from(settlement)
                        .where(where)
                        .orderBy(settlement.periodStart.desc(), settlement.id.desc())
                        .limit(size + 1L)
                        .fetch()
                        .stream()
                        .map(SettlementQueryDao::toSettlement)
                        .toList();
        return cursorPage(
                rows,
                size,
                countSettlements(base),
                v -> new Cursor(v.periodStart(), v.settlementId()));
    }

    /** 관리자 목록. 최근 확정 순. 조건이 null이면 거르지 않는다. 인덱스 {@code settlement_confirmed_idx}. */
    public PageView<SettlementView> findAdminPage(
            @Nullable SettlementStatus status, @Nullable UUID sellerId, int page, int size) {
        BooleanBuilder where = new BooleanBuilder();
        if (status != null) {
            where.and(settlement.status.stringValue().eq(status.name()));
        }
        if (sellerId != null) {
            where.and(settlement.sellerId.eq(sellerId));
        }
        List<SettlementView> items =
                query
                        .select(SETTLEMENT_COLUMNS)
                        .from(settlement)
                        .where(where)
                        .orderBy(settlement.confirmedAt.desc(), settlement.id.desc())
                        .offset((long) page * size)
                        .limit(size)
                        .fetch()
                        .stream()
                        .map(SettlementQueryDao::toSettlement)
                        .toList();
        return new PageView<>(items, countSettlements(where), page, size);
    }

    /** 정산의 대상. 인덱스 {@code settlement_item_settlement_idx}. */
    public CursorPageView<SettlementItemView> findSettlementItems(
            UUID settlementId, @Nullable String cursor, int size) {
        return itemPage(settlementItem.settlementId.eq(settlementId), cursor, size);
    }

    /** 판매자의 확정되지 않은 대상(정산 예정 내역). 인덱스 {@code settlement_item_pending_idx}. */
    public CursorPageView<SettlementItemView> findPendingItems(
            UUID sellerId, @Nullable String cursor, int size) {
        return itemPage(pending(sellerId), cursor, size);
    }

    /** 판매자의 확정되지 않은 대상 합계. */
    public PendingSettlementSummaryView pendingSummary(UUID sellerId) {
        Tuple row =
                query.select(
                                settlementItem.count(),
                                settlementItem.salesAmount.sumLong(),
                                settlementItem.commissionAmount.sumLong(),
                                settlementItem.sellerCouponDiscount.sumLong(),
                                settlementItem.settlementAmount.sumLong())
                        .from(settlementItem)
                        .where(pending(sellerId))
                        .fetchOne();
        if (row == null) {
            return new PendingSettlementSummaryView(0, 0, 0, 0, 0);
        }
        return new PendingSettlementSummaryView(
                orZero(row.get(0, Long.class)),
                orZero(row.get(1, Long.class)),
                orZero(row.get(2, Long.class)),
                orZero(row.get(3, Long.class)),
                orZero(row.get(4, Long.class)));
    }

    private static BooleanExpression pending(UUID sellerId) {
        return settlementItem.sellerId.eq(sellerId).and(settlementItem.settlementId.isNull());
    }

    private CursorPageView<SettlementItemView> itemPage(
            BooleanExpression base, @Nullable String cursor, int size) {
        BooleanBuilder where = new BooleanBuilder(base);
        if (cursor != null) {
            where.and(after(settlementItem.confirmedAt, settlementItem.id, cursor));
        }
        List<Tuple> rows =
                query.select(ITEM_COLUMNS)
                        .from(settlementItem)
                        .where(where)
                        .orderBy(settlementItem.confirmedAt.desc(), settlementItem.id.desc())
                        .limit(size + 1L)
                        .fetch();
        boolean hasNext = rows.size() > size;
        List<Tuple> page = hasNext ? rows.subList(0, size) : rows;
        Map<UUID, List<SettlementItemLineView>> lines =
                linesOf(
                        page.stream()
                                .map(t -> Objects.requireNonNull(t.get(settlementItem.id)))
                                .toList());
        List<SettlementItemView> items =
                page.stream()
                        .map(
                                t -> {
                                    UUID id = Objects.requireNonNull(t.get(settlementItem.id));
                                    return new SettlementItemView(
                                            Objects.requireNonNull(
                                                    t.get(settlementItem.sellerOrderId)),
                                            Objects.requireNonNull(t.get(settlementItem.orderId)),
                                            Objects.requireNonNull(
                                                    t.get(settlementItem.confirmedAt)),
                                            Objects.requireNonNull(
                                                    t.get(settlementItem.periodStart)),
                                            Objects.requireNonNull(t.get(settlementItem.periodEnd)),
                                            orZero(t.get(settlementItem.salesAmount)),
                                            orZero(t.get(settlementItem.commissionAmount)),
                                            orZero(t.get(settlementItem.sellerCouponDiscount)),
                                            orZero(t.get(settlementItem.settlementAmount)),
                                            lines.getOrDefault(id, List.of()));
                                })
                        .toList();
        Long total =
                query.select(settlementItem.count()).from(settlementItem).where(base).fetchOne();
        String nextCursor =
                hasNext
                        ? Cursor.encode(
                                new Cursor(
                                        Objects.requireNonNull(
                                                page.getLast().get(settlementItem.confirmedAt)),
                                        Objects.requireNonNull(
                                                page.getLast().get(settlementItem.id))))
                        : null;
        return new CursorPageView<>(items, total == null ? 0 : total, nextCursor);
    }

    /** 대상마다 주문 항목을 넣은 순서대로. */
    private Map<UUID, List<SettlementItemLineView>> linesOf(List<UUID> itemIds) {
        Map<UUID, List<SettlementItemLineView>> lines = new LinkedHashMap<>();
        if (itemIds.isEmpty()) {
            return lines;
        }
        for (Tuple t :
                query.select(
                                settlementItem.id,
                                line.orderLineId,
                                line.productId,
                                line.productName,
                                line.categoryId,
                                line.quantity,
                                line.amount,
                                line.commissionRateBp,
                                line.commissionAmount)
                        .from(settlementItem)
                        .join(settlementItem.lines, line)
                        .where(settlementItem.id.in(itemIds))
                        .orderBy(settlementItem.id.asc(), line.position.asc())
                        .fetch()) {
            lines.computeIfAbsent(
                            Objects.requireNonNull(t.get(settlementItem.id)),
                            k -> new ArrayList<>())
                    .add(
                            new SettlementItemLineView(
                                    Objects.requireNonNull(t.get(line.orderLineId)),
                                    Objects.requireNonNull(t.get(line.productId)),
                                    Objects.requireNonNull(t.get(line.productName)),
                                    Objects.requireNonNull(t.get(line.categoryId)),
                                    Objects.requireNonNull(t.get(line.quantity)),
                                    orZero(t.get(line.amount)),
                                    Objects.requireNonNull(t.get(line.commissionRateBp)),
                                    orZero(t.get(line.commissionAmount))));
        }
        return lines;
    }

    private long countSettlements(Predicate where) {
        Long total = query.select(settlement.count()).from(settlement).where(where).fetchOne();
        return total == null ? 0 : total;
    }

    private static SettlementView toSettlement(Tuple t) {
        return new SettlementView(
                Objects.requireNonNull(t.get(settlement.id)),
                Objects.requireNonNull(t.get(settlement.sellerId)),
                Objects.requireNonNull(t.get(settlement.periodStart)),
                Objects.requireNonNull(t.get(settlement.periodEnd)),
                Objects.requireNonNull(t.get(settlement.itemCount)),
                orZero(t.get(settlement.salesAmount)),
                orZero(t.get(settlement.commissionAmount)),
                orZero(t.get(settlement.sellerCouponDiscount)),
                orZero(t.get(settlement.amount)),
                SettlementStatus.valueOf(Objects.requireNonNull(t.get(9, String.class))),
                Objects.requireNonNull(t.get(settlement.confirmedAt)),
                t.get(settlement.paidOutAt));
    }

    private static long orZero(@Nullable Long value) {
        return value == null ? 0 : value;
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
                    ErrorType.INVALID, "SETTLEMENT_INVALID_CURSOR", "정산 목록 커서가 올바르지 않습니다.");
        }
    }
}
