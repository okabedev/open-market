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
import java.util.Objects;
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

    // 주문에 쓴 플랫폼 쿠폰. 판매자 쿠폰은 하위 주문에 있다.
    @Column(name = "platform_issued_coupon_id")
    private @Nullable UUID platformIssuedCouponId;

    // 받은 예약 결과(docs/architecture/order.md "예약 결과"). 결제 전에만 쓴다.
    @Column(name = "stock_reserved", nullable = false)
    private boolean stockReserved;

    @Column(name = "coupon_reserved", nullable = false)
    private boolean couponReserved;

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
        order.updateAmount();
        return order;
    }

    /**
     * promotion이 계산한 쿠폰 할인을 하위 주문에 기록한다(docs/architecture/order.md "쿠폰 적용"). 주문 금액은 하위 주문 결제 금액의 합이
     * 된다. 주문을 만든 직후 저장하기 전에 한 번만 부른다.
     *
     * @param discounts 하위 주문 순서대로 하나씩
     * @throws IllegalArgumentException 할인이 하위 주문과 맞지 않는다(판매자 순서, 음수, 하위 주문 금액 초과, 쿠폰 없는 할인)
     */
    public void applyCoupons(
            @Nullable UUID platformIssuedCouponId, List<SellerOrderDiscount> discounts) {
        if (status != OrderStatus.PENDING || !issuedCouponIds().isEmpty()) {
            throw new IllegalStateException("쿠폰은 주문을 만들 때 한 번만 적용한다: orderId=" + id);
        }
        if (discounts.size() != sellerOrders.size()) {
            throw new IllegalArgumentException("할인 수가 하위 주문 수와 다르다");
        }
        for (int i = 0; i < discounts.size(); i++) {
            SellerOrderDiscount discount = discounts.get(i);
            SellerOrder sellerOrder = sellerOrders.get(i);
            if (!sellerOrder.sellerId().equals(discount.sellerId())) {
                throw new IllegalArgumentException("할인의 판매자가 하위 주문 순서와 다르다");
            }
            if (platformIssuedCouponId == null && discount.platformCouponDiscount() != 0) {
                throw new IllegalArgumentException("플랫폼 쿠폰 없이 플랫폼 할인이 있다");
            }
            sellerOrder.applyDiscount(discount);
        }
        this.platformIssuedCouponId = platformIssuedCouponId;
        updateAmount();
    }

    private void updateAmount() {
        amount =
                sellerOrders.stream()
                        .mapToLong(SellerOrder::paymentAmount)
                        .reduce(0, Math::addExact);
    }

    /** 주문에 쓴 발급 쿠폰. 플랫폼 쿠폰, 하위 주문 순서의 판매자 쿠폰 순이다. 쿠폰이 없으면 비어 있다. */
    public List<UUID> issuedCouponIds() {
        List<UUID> ids = new ArrayList<>();
        if (platformIssuedCouponId != null) {
            ids.add(platformIssuedCouponId);
        }
        sellerOrders.stream()
                .map(SellerOrder::sellerIssuedCouponId)
                .filter(Objects::nonNull)
                .forEach(ids::add);
        return ids;
    }

    /** 쿠폰 예약 결과도 기다려야 하는가. */
    public boolean requiresCouponReservation() {
        return !issuedCouponIds().isEmpty();
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

    /**
     * 예약 성공 결과를 기록한다(R12, docs/architecture/order.md "예약 결과"). 필요한 예약(재고, 쿠폰이 있으면 쿠폰)을 모두 받으면
     * {@code PENDING} → {@code AWAITING_PAYMENT}다. 실패 결과는 {@link #cancel}이다.
     */
    public ReservationOutcome recordReservation(Reservation reservation, Instant now) {
        if (reservation == Reservation.COUPON && !requiresCouponReservation()) {
            return ReservationOutcome.INVALID;
        }
        if (status == OrderStatus.AWAITING_PAYMENT) {
            return ReservationOutcome.ALREADY;
        }
        if (status != OrderStatus.PENDING) {
            return ReservationOutcome.INVALID;
        }
        boolean already = reservation == Reservation.STOCK ? stockReserved : couponReserved;
        if (already) {
            return ReservationOutcome.ALREADY;
        }
        if (reservation == Reservation.STOCK) {
            stockReserved = true;
        } else {
            couponReserved = true;
        }
        updatedAt = now;
        if (!stockReserved || (requiresCouponReservation() && !couponReserved)) {
            return ReservationOutcome.RECORDED;
        }
        status = OrderStatus.AWAITING_PAYMENT;
        return ReservationOutcome.AWAITING_PAYMENT;
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
        for (SellerOrder sellerOrder : sellerOrders) {
            sellerOrder.startProgress(now);
        }
        return Transition.APPLIED;
    }

    /**
     * 하위 주문 진행 상태를 앞으로 바꾼다(fulfillment 배송 이벤트). 같거나 뒤의 단계면 {@code ALREADY}(재처리, 늦게 온 이벤트), 결제 전
     * 주문이거나 이 주문의 하위 주문이 아니면 {@code INVALID}다.
     */
    public Transition advanceSellerOrder(
            UUID sellerOrderId, SellerOrderProgress progress, Instant now) {
        if (status != OrderStatus.PAID) {
            return Transition.INVALID;
        }
        for (SellerOrder sellerOrder : sellerOrders) {
            if (sellerOrder.id().equals(sellerOrderId)) {
                if (!sellerOrder.advance(progress, now)) {
                    return Transition.ALREADY;
                }
                updatedAt = now;
                return Transition.APPLIED;
            }
        }
        return Transition.INVALID;
    }

    /**
     * 하위 주문 클레임을 반영한다(fulfillment 클레임 이벤트). 같은 클레임의 같은 상태이거나 이미 끝난 클레임이면 {@code ALREADY}, 결제 전
     * 주문이거나 이 주문의 하위 주문이 아니면 {@code INVALID}다.
     */
    public Transition applyClaim(
            UUID sellerOrderId, UUID claimId, ClaimType type, ClaimStatus status, Instant now) {
        if (this.status != OrderStatus.PAID) {
            return Transition.INVALID;
        }
        for (SellerOrder sellerOrder : sellerOrders) {
            if (sellerOrder.id().equals(sellerOrderId)) {
                if (!sellerOrder.applyClaim(claimId, type, status, now)) {
                    return Transition.ALREADY;
                }
                updatedAt = now;
                return Transition.APPLIED;
            }
        }
        return Transition.INVALID;
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

    /** 결제 금액(원). 하위 주문 결제 금액의 합이다. */
    public long amount() {
        return amount;
    }

    public @Nullable UUID platformIssuedCouponId() {
        return platformIssuedCouponId;
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

    /** 주문이 기다리는 예약. */
    public enum Reservation {
        /** 재고 예약(catalog). */
        STOCK,
        /** 쿠폰 예약(promotion). 쿠폰이 있는 주문만. */
        COUPON
    }

    /** 예약 결과 기록의 결과. */
    public enum ReservationOutcome {
        /** 필요한 예약을 모두 받아 결제 대기가 됐다. */
        AWAITING_PAYMENT,
        /** 기록했다. 다른 예약 결과를 기다린다. */
        RECORDED,
        /** 이미 기록했거나 이미 결제 대기다. 아무것도 하지 않았다. */
        ALREADY,
        /** 지금 상태에서는 할 수 없다(취소됨, 결제됨, 쿠폰 없는 주문의 쿠폰 결과). 부르는 쪽이 경고 로그를 남기고 넘긴다. */
        INVALID
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
