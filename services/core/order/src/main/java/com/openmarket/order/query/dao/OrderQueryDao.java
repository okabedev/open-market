package com.openmarket.order.query.dao;

import static com.openmarket.order.command.domain.QOrder.order;
import static com.openmarket.order.command.domain.QOrderLine.orderLine;
import static com.openmarket.order.command.domain.QSellerOrder.sellerOrder;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;
import com.openmarket.order.command.domain.QOrderLine;
import com.openmarket.order.query.dto.CancelReason;
import com.openmarket.order.query.dto.CursorPageView;
import com.openmarket.order.query.dto.OrderDetailView;
import com.openmarket.order.query.dto.OrderStatus;
import com.openmarket.order.query.dto.OrderSummaryView;
import com.openmarket.order.query.dto.PageView;
import com.openmarket.order.query.dto.SellerOrderDetailView;
import com.openmarket.order.query.dto.SellerOrderSummaryView;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Predicate;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

/** 주문 조회. 주문 한 건은 주문 행, 하위 주문, 항목을 따로 읽는다(항목 50개 상한). */
@Repository
public class OrderQueryDao {

    private final JPAQueryFactory query;

    public OrderQueryDao(JPAQueryFactory query) {
        this.query = query;
    }

    /** 구매자 본인 주문. 다른 회원의 주문이면 비어 있다. */
    public Optional<OrderDetailView> findForBuyer(UUID buyerId, UUID orderId) {
        Tuple row =
                query.select(
                                order.status.stringValue(),
                                order.cancelReason.stringValue(),
                                order.amount,
                                order.orderName,
                                order.shippingAddress.recipientName,
                                order.shippingAddress.recipientPhone,
                                order.shippingAddress.zipCode,
                                order.shippingAddress.baseAddress,
                                order.shippingAddress.detailAddress,
                                order.placedAt)
                        .from(order)
                        .where(order.id.eq(orderId), order.buyerId.eq(buyerId))
                        .fetchOne();
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(
                new OrderDetailView(
                        orderId,
                        OrderStatus.valueOf(Objects.requireNonNull(row.get(0, String.class))),
                        cancelReason(row.get(1, String.class)),
                        Objects.requireNonNull(row.get(order.amount)),
                        Objects.requireNonNull(row.get(order.orderName)),
                        shipping(row),
                        sellerOrders(orderId),
                        Objects.requireNonNull(row.get(order.placedAt))));
    }

    /**
     * 구매자 본인 주문 목록. 최근 주문 순이고, 주문 시각이 같으면 주문 ID 역순이다.
     *
     * @param cursor 이전 쪽의 {@code nextCursor}. 형식이 틀리면 400 {@code ORDER_INVALID_CURSOR}
     */
    public CursorPageView<OrderSummaryView> findBuyerOrders(
            UUID buyerId, @Nullable String cursor, int size) {
        BooleanBuilder where = new BooleanBuilder(order.buyerId.eq(buyerId));
        if (cursor != null) {
            Cursor after = Cursor.decode(cursor);
            where.and(
                    order.placedAt
                            .lt(after.placedAt())
                            .or(order.placedAt.eq(after.placedAt()).and(order.id.lt(after.id()))));
        }
        List<OrderSummaryView> rows =
                query
                        .select(
                                order.id,
                                order.status.stringValue(),
                                order.cancelReason.stringValue(),
                                order.amount,
                                order.orderName,
                                order.placedAt)
                        .from(order)
                        .where(where)
                        .orderBy(order.placedAt.desc(), order.id.desc())
                        .limit(size + 1L)
                        .fetch()
                        .stream()
                        .map(
                                t ->
                                        new OrderSummaryView(
                                                Objects.requireNonNull(t.get(order.id)),
                                                OrderStatus.valueOf(
                                                        Objects.requireNonNull(
                                                                t.get(1, String.class))),
                                                cancelReason(t.get(2, String.class)),
                                                Objects.requireNonNull(t.get(order.amount)),
                                                Objects.requireNonNull(t.get(order.orderName)),
                                                Objects.requireNonNull(t.get(order.placedAt))))
                        .toList();
        boolean hasNext = rows.size() > size;
        List<OrderSummaryView> items = hasNext ? rows.subList(0, size) : rows;
        Long total =
                query.select(order.count()).from(order).where(order.buyerId.eq(buyerId)).fetchOne();
        String nextCursor =
                hasNext
                        ? Cursor.encode(
                                new Cursor(items.getLast().placedAt(), items.getLast().orderId()))
                        : null;
        return new CursorPageView<>(List.copyOf(items), total == null ? 0 : total, nextCursor);
    }

    /** 판매자 본인 하위 주문 목록. 주문 상태가 {@code statuses} 중 하나인 것만. 최근 주문 순, 쪽 번호. */
    public PageView<SellerOrderSummaryView> findSellerOrders(
            UUID sellerId, Collection<OrderStatus> statuses, int page, int size) {
        Predicate where = sellerVisible(sellerId, statuses);
        QOrderLine first = new QOrderLine("first");
        QOrderLine counted = new QOrderLine("counted");
        List<SellerOrderSummaryView> items =
                query
                        .select(
                                sellerOrder.id,
                                order.id,
                                order.status.stringValue(),
                                sellerOrder.amount,
                                JPAExpressions.select(first.productName)
                                        .from(first)
                                        .where(
                                                first.sellerOrder.id.eq(sellerOrder.id),
                                                first.position.eq(0)),
                                JPAExpressions.select(counted.count())
                                        .from(counted)
                                        .where(counted.sellerOrder.id.eq(sellerOrder.id)),
                                order.placedAt)
                        .from(sellerOrder)
                        .join(sellerOrder.order, order)
                        .where(where)
                        .orderBy(order.placedAt.desc(), sellerOrder.id.desc())
                        .offset((long) page * size)
                        .limit(size)
                        .fetch()
                        .stream()
                        .map(
                                t ->
                                        new SellerOrderSummaryView(
                                                Objects.requireNonNull(t.get(sellerOrder.id)),
                                                Objects.requireNonNull(t.get(order.id)),
                                                OrderStatus.valueOf(
                                                        Objects.requireNonNull(
                                                                t.get(2, String.class))),
                                                Objects.requireNonNull(t.get(sellerOrder.amount)),
                                                Objects.requireNonNull(t.get(4, String.class)),
                                                Objects.requireNonNull(t.get(5, Long.class))
                                                        .intValue(),
                                                Objects.requireNonNull(t.get(order.placedAt))))
                        .toList();
        Long total =
                query.select(sellerOrder.count())
                        .from(sellerOrder)
                        .join(sellerOrder.order, order)
                        .where(where)
                        .fetchOne();
        return new PageView<>(items, total == null ? 0 : total, page, size);
    }

    /** 판매자 본인 하위 주문. 다른 판매자의 것이거나 주문 상태가 {@code statuses}에 없으면 비어 있다. */
    public Optional<SellerOrderDetailView> findForSeller(
            UUID sellerId, UUID sellerOrderId, Collection<OrderStatus> statuses) {
        Tuple row =
                query.select(
                                order.id,
                                order.status.stringValue(),
                                sellerOrder.amount,
                                order.shippingAddress.recipientName,
                                order.shippingAddress.recipientPhone,
                                order.shippingAddress.zipCode,
                                order.shippingAddress.baseAddress,
                                order.shippingAddress.detailAddress,
                                order.placedAt)
                        .from(sellerOrder)
                        .join(sellerOrder.order, order)
                        .where(sellerOrder.id.eq(sellerOrderId), sellerVisible(sellerId, statuses))
                        .fetchOne();
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(
                new SellerOrderDetailView(
                        sellerOrderId,
                        Objects.requireNonNull(row.get(order.id)),
                        OrderStatus.valueOf(Objects.requireNonNull(row.get(1, String.class))),
                        Objects.requireNonNull(row.get(sellerOrder.amount)),
                        shipping(row),
                        List.copyOf(
                                lines(orderLine.sellerOrder.id.eq(sellerOrderId))
                                        .getOrDefault(sellerOrderId, List.of())),
                        Objects.requireNonNull(row.get(order.placedAt))));
    }

    private static Predicate sellerVisible(UUID sellerId, Collection<OrderStatus> statuses) {
        return sellerOrder
                .sellerId
                .eq(sellerId)
                .and(
                        order.status
                                .stringValue()
                                .in(statuses.stream().map(OrderStatus::name).toList()));
    }

    private List<OrderDetailView.SellerOrder> sellerOrders(UUID orderId) {
        List<Tuple> sellerRows =
                query.select(
                                sellerOrder.id,
                                sellerOrder.sellerId,
                                sellerOrder.storeName,
                                sellerOrder.amount)
                        .from(sellerOrder)
                        .where(sellerOrder.order.id.eq(orderId))
                        .orderBy(sellerOrder.position.asc())
                        .fetch();
        Map<UUID, List<OrderDetailView.Line>> lines =
                lines(orderLine.sellerOrder.order.id.eq(orderId));
        return sellerRows.stream()
                .map(
                        r -> {
                            UUID id = Objects.requireNonNull(r.get(sellerOrder.id));
                            return new OrderDetailView.SellerOrder(
                                    id,
                                    Objects.requireNonNull(r.get(sellerOrder.sellerId)),
                                    Objects.requireNonNull(r.get(sellerOrder.storeName)),
                                    Objects.requireNonNull(r.get(sellerOrder.amount)),
                                    List.copyOf(lines.getOrDefault(id, List.of())));
                        })
                .toList();
    }

    /** 항목을 하위 주문별로 묶는다. 하위 주문 안에서는 주문한 순서다. */
    private Map<UUID, List<OrderDetailView.Line>> lines(Predicate where) {
        Map<UUID, List<OrderDetailView.Line>> lines = new LinkedHashMap<>();
        query.select(
                        orderLine.sellerOrder.id,
                        orderLine.id,
                        orderLine.skuId,
                        orderLine.productId,
                        orderLine.productName,
                        orderLine.optionName1,
                        orderLine.optionValue1,
                        orderLine.optionName2,
                        orderLine.optionValue2,
                        orderLine.unitPrice,
                        orderLine.quantity,
                        orderLine.amount)
                .from(orderLine)
                .where(where)
                .orderBy(orderLine.position.asc())
                .fetch()
                .forEach(
                        t ->
                                lines.computeIfAbsent(
                                                Objects.requireNonNull(
                                                        t.get(orderLine.sellerOrder.id)),
                                                id -> new ArrayList<>())
                                        .add(
                                                new OrderDetailView.Line(
                                                        Objects.requireNonNull(t.get(orderLine.id)),
                                                        Objects.requireNonNull(
                                                                t.get(orderLine.skuId)),
                                                        Objects.requireNonNull(
                                                                t.get(orderLine.productId)),
                                                        Objects.requireNonNull(
                                                                t.get(orderLine.productName)),
                                                        pair(
                                                                t.get(orderLine.optionName1),
                                                                t.get(orderLine.optionName2)),
                                                        pair(
                                                                t.get(orderLine.optionValue1),
                                                                t.get(orderLine.optionValue2)),
                                                        Objects.requireNonNull(
                                                                t.get(orderLine.unitPrice)),
                                                        Objects.requireNonNull(
                                                                t.get(orderLine.quantity)),
                                                        Objects.requireNonNull(
                                                                t.get(orderLine.amount)))));
        return lines;
    }

    /** 주문 행의 배송지 컬럼으로 배송지를 만든다. */
    private static OrderDetailView.Shipping shipping(Tuple row) {
        return new OrderDetailView.Shipping(
                Objects.requireNonNull(row.get(order.shippingAddress.recipientName)),
                Objects.requireNonNull(row.get(order.shippingAddress.recipientPhone)),
                Objects.requireNonNull(row.get(order.shippingAddress.zipCode)),
                Objects.requireNonNull(row.get(order.shippingAddress.baseAddress)),
                row.get(order.shippingAddress.detailAddress));
    }

    private static @Nullable CancelReason cancelReason(@Nullable String value) {
        return value == null ? null : CancelReason.valueOf(value);
    }

    /** 컬럼 두 개에 나눠 둔 옵션 이름·값을 목록으로. */
    private static List<String> pair(@Nullable String first, @Nullable String second) {
        List<String> values = new ArrayList<>(2);
        if (first != null) {
            values.add(first);
        }
        if (second != null) {
            values.add(second);
        }
        return List.copyOf(values);
    }

    /**
     * 주문 목록 커서. 마지막 주문의 주문 시각과 ID를 {@code <ISO 시각>|<UUID>}로 적어 base64url로 감싼다. 클라이언트에는 불투명한 문자열이다.
     * 형식이 틀리면 400 {@code ORDER_INVALID_CURSOR}.
     */
    record Cursor(Instant placedAt, UUID id) {

        private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
        private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
        private static final int MAX_LENGTH = 200;

        static String encode(Cursor cursor) {
            return ENCODER.encodeToString(
                    (cursor.placedAt() + "|" + cursor.id()).getBytes(StandardCharsets.UTF_8));
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
                    ErrorType.INVALID, "ORDER_INVALID_CURSOR", "주문 목록 커서가 올바르지 않습니다.");
        }
    }
}
