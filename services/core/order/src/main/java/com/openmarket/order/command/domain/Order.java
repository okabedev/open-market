package com.openmarket.order.command.domain;

import com.openmarket.common.core.id.Ids;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 주문(docs/glossary.md "주문"). 구매자의 결제 단위이고 판매자별 하위 주문({@link SellerOrder})으로 나뉜다. 규칙은
 * docs/architecture/order.md "주문". 테이블 이름은 예약어 {@code order}를 피해 {@code orders}다.
 */
@Entity
@Table(name = "orders")
public class Order {

    /** 주문명 상한. 토스 {@code orderName}의 길이 상한이다. */
    public static final int ORDER_NAME_MAX_LENGTH = 100;

    @Id private UUID id;

    @Column(name = "buyer_id", nullable = false)
    private UUID buyerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancel_reason")
    private @Nullable CancelReason cancelReason;

    @Column(nullable = false)
    private long amount;

    @Column(name = "order_name", nullable = false)
    private String orderName;

    @Embedded private ShippingAddress shippingAddress;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<SellerOrder> sellerOrders = new ArrayList<>();

    @Column(name = "placed_at", nullable = false)
    private Instant placedAt;

    @Column(name = "paid_at")
    private @Nullable Instant paidAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version private long version;

    protected Order() {}

    private Order(
            UUID buyerId, List<OrderItem> items, ShippingAddress shippingAddress, Instant now) {
        this.id = Ids.newId();
        this.orderName = orderName(items);
        this.buyerId = buyerId;
        this.status = OrderStatus.PENDING;
        this.shippingAddress = shippingAddress;
        this.placedAt = now;
        this.updatedAt = now;
    }

    /**
     * 주문을 {@code PENDING}으로 만든다. 판매자가 처음 나온 순서로 하위 주문을 나누고, 항목은 주어진 순서다.
     *
     * @param items 1개 이상. 확인은 부르는 쪽이 끝냈다
     */
    public static Order place(
            UUID buyerId, List<OrderItem> items, ShippingAddress shippingAddress, Instant now) {
        if (items.isEmpty()) {
            throw new IllegalArgumentException("주문 항목이 없다");
        }
        Order order = new Order(buyerId, items, shippingAddress, now);
        Map<UUID, SellerOrder> bySeller = new LinkedHashMap<>();
        for (OrderItem item : items) {
            SellerOrder sellerOrder =
                    bySeller.computeIfAbsent(
                            item.sellerId(),
                            sellerId -> {
                                SellerOrder created =
                                        new SellerOrder(
                                                order,
                                                order.sellerOrders.size(),
                                                sellerId,
                                                item.storeName());
                                order.sellerOrders.add(created);
                                return created;
                            });
            sellerOrder.add(item);
        }
        order.amount =
                order.sellerOrders.stream()
                        .mapToLong(SellerOrder::amount)
                        .reduce(0, Math::addExact);
        return order;
    }

    /** 첫 항목의 상품명. 둘 이상이면 {@code <상품명> 외 <n>건}. 100자를 넘으면 상품명을 줄인다. */
    static String orderName(List<OrderItem> items) {
        String suffix = items.size() > 1 ? " 외 " + (items.size() - 1) + "건" : "";
        String name = items.getFirst().productName();
        int room = ORDER_NAME_MAX_LENGTH - suffix.length();
        if (name.codePointCount(0, name.length()) > room) {
            name = name.substring(0, name.offsetByCodePoints(0, room - 1)) + "…";
        }
        return name + suffix;
    }

    /** 재고 예약이 끝나 결제를 기다린다. {@code PENDING}에서만 바뀐다. */
    public Transition awaitPayment(Instant now) {
        if (status == OrderStatus.AWAITING_PAYMENT) {
            return Transition.ALREADY;
        }
        if (status != OrderStatus.PENDING) {
            return Transition.INVALID;
        }
        status = OrderStatus.AWAITING_PAYMENT;
        updatedAt = now;
        return Transition.APPLIED;
    }

    /**
     * 결제가 승인돼 결제 완료가 된다. {@code AWAITING_PAYMENT}에서만 바뀐다.
     *
     * @param amount 승인된 금액(원). payment가 주문 금액을 고정하므로 다르면 버그다
     * @param approvedAt 토스 승인 시각. 결제 시각이 된다
     * @throws IllegalStateException 결제 대기 주문인데 금액이 주문 금액과 다르다
     */
    public Transition pay(long amount, Instant approvedAt, Instant now) {
        if (status == OrderStatus.PAID) {
            return Transition.ALREADY;
        }
        if (status != OrderStatus.AWAITING_PAYMENT) {
            return Transition.INVALID;
        }
        if (amount != this.amount) {
            throw new IllegalStateException("승인 금액이 주문 금액과 다르다: orderId=" + id);
        }
        status = OrderStatus.PAID;
        paidAt = approvedAt;
        updatedAt = now;
        return Transition.APPLIED;
    }

    /** 결제 전에 취소한다. {@code PENDING}, {@code AWAITING_PAYMENT}에서만 바뀐다. 이미 취소됐으면 처음 사유가 남는다. */
    public Transition cancel(CancelReason reason, Instant now) {
        if (status == OrderStatus.CANCELLED) {
            return Transition.ALREADY;
        }
        if (status != OrderStatus.PENDING && status != OrderStatus.AWAITING_PAYMENT) {
            return Transition.INVALID;
        }
        status = OrderStatus.CANCELLED;
        cancelReason = reason;
        updatedAt = now;
        return Transition.APPLIED;
    }

    /** 주문의 모든 항목. 하위 주문 순서, 그 안의 항목 순서다. */
    public List<OrderLine> lines() {
        return sellerOrders.stream().flatMap(sellerOrder -> sellerOrder.lines().stream()).toList();
    }

    public UUID id() {
        return id;
    }

    public UUID buyerId() {
        return buyerId;
    }

    public OrderStatus status() {
        return status;
    }

    public @Nullable CancelReason cancelReason() {
        return cancelReason;
    }

    public long amount() {
        return amount;
    }

    public String orderName() {
        return orderName;
    }

    public @Nullable Instant paidAt() {
        return paidAt;
    }

    public ShippingAddress shippingAddress() {
        return shippingAddress;
    }

    public List<SellerOrder> sellerOrders() {
        return Collections.unmodifiableList(sellerOrders);
    }

    /** 상태 전이 결과. */
    public enum Transition {
        /** 바꿨다. */
        APPLIED,
        /** 이미 그 상태다. 아무것도 하지 않았다. */
        ALREADY,
        /** 지금 상태에서는 할 수 없다. 부르는 쪽이 경고 로그를 남기고 넘긴다. */
        INVALID
    }
}
