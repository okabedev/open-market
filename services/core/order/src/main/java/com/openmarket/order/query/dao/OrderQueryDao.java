package com.openmarket.order.query.dao;

import static com.openmarket.order.command.domain.QOrder.order;
import static com.openmarket.order.command.domain.QOrderLine.orderLine;
import static com.openmarket.order.command.domain.QSellerOrder.sellerOrder;

import com.openmarket.order.query.dto.CancelReason;
import com.openmarket.order.query.dto.OrderDetailView;
import com.openmarket.order.query.dto.OrderStatus;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.ArrayList;
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
        @Nullable String cancelReason = row.get(1, String.class);
        return Optional.of(
                new OrderDetailView(
                        orderId,
                        OrderStatus.valueOf(Objects.requireNonNull(row.get(0, String.class))),
                        cancelReason == null ? null : CancelReason.valueOf(cancelReason),
                        Objects.requireNonNull(row.get(order.amount)),
                        Objects.requireNonNull(row.get(order.orderName)),
                        new OrderDetailView.Shipping(
                                Objects.requireNonNull(
                                        row.get(order.shippingAddress.recipientName)),
                                Objects.requireNonNull(
                                        row.get(order.shippingAddress.recipientPhone)),
                                Objects.requireNonNull(row.get(order.shippingAddress.zipCode)),
                                Objects.requireNonNull(row.get(order.shippingAddress.baseAddress)),
                                row.get(order.shippingAddress.detailAddress)),
                        sellerOrders(orderId),
                        Objects.requireNonNull(row.get(order.placedAt))));
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
                .where(orderLine.sellerOrder.order.id.eq(orderId))
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
}
