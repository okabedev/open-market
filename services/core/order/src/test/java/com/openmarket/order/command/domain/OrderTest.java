package com.openmarket.order.command.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderTest {

    static final Instant NOW = Instant.parse("2026-10-20T00:00:00Z");
    static final UUID CATEGORY = UUID.fromString("0199f000-0000-7000-8000-000000000101");

    final UUID buyerId = UUID.randomUUID();
    final UUID sellerA = UUID.randomUUID();
    final UUID sellerB = UUID.randomUUID();
    final ShippingAddress address =
            ShippingAddress.of("홍길동", "010-1234-5678", "06236", "서울 강남구 테헤란로 1", null);

    @Test
    @DisplayName("주문은 판매자가 처음 나온 순서로 하위 주문을 나누고, 금액은 항목 금액의 합이다")
    void placesWithSellerOrders() {
        Order order =
                Order.place(
                        buyerId,
                        List.of(
                                item(sellerA, "반팔 티셔츠", 19_900, 2),
                                item(sellerB, "청바지", 39_900, 1),
                                item(sellerA, "양말", 3_000, 3)),
                        address,
                        NOW);

        assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.amount()).isEqualTo(19_900 * 2 + 39_900 + 3_000 * 3);
        assertThat(order.sellerOrders()).hasSize(2);
        assertThat(order.sellerOrders().get(0).sellerId()).isEqualTo(sellerA);
        assertThat(order.sellerOrders().get(0).amount()).isEqualTo(19_900 * 2 + 3_000 * 3);
        assertThat(order.sellerOrders().get(0).lines())
                .extracting(OrderLine::productName)
                .containsExactly("반팔 티셔츠", "양말");
        assertThat(order.sellerOrders().get(1).amount()).isEqualTo(39_900);
        assertThat(order.orderName()).isEqualTo("반팔 티셔츠 외 2건");
    }

    @Test
    @DisplayName("항목이 하나면 주문명은 상품명이고, 100자를 넘으면 상품명을 줄인다")
    void orderName() {
        assertThat(
                        Order.place(buyerId, List.of(item(sellerA, "청바지", 1_000, 1)), address, NOW)
                                .orderName())
                .isEqualTo("청바지");

        String longName = "가".repeat(100);
        String name =
                Order.place(
                                buyerId,
                                List.of(
                                        item(sellerA, longName, 1_000, 1),
                                        item(sellerA, "양말", 1_000, 1)),
                                address,
                                NOW)
                        .orderName();
        assertThat(name).hasSizeLessThanOrEqualTo(Order.ORDER_NAME_MAX_LENGTH).endsWith("… 외 1건");
    }

    @Test
    @DisplayName("예약이 끝나면 결제 대기가 된다. 다시 와도 그대로이고, 취소된 주문은 바뀌지 않는다")
    void awaitPayment() {
        Order order = Order.place(buyerId, List.of(item(sellerA, "청바지", 1_000, 1)), address, NOW);

        assertThat(order.awaitPayment(NOW)).isEqualTo(Order.Transition.APPLIED);
        assertThat(order.status()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
        assertThat(order.awaitPayment(NOW)).isEqualTo(Order.Transition.ALREADY);

        Order cancelled =
                Order.place(buyerId, List.of(item(sellerA, "청바지", 1_000, 1)), address, NOW);
        cancelled.cancel(CancelReason.STOCK_UNAVAILABLE, NOW);
        assertThat(cancelled.awaitPayment(NOW)).isEqualTo(Order.Transition.INVALID);
        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("결제 전 주문은 취소된다. 사유가 남고, 다시 취소해도 그대로다")
    void cancel() {
        Order order = Order.place(buyerId, List.of(item(sellerA, "청바지", 1_000, 1)), address, NOW);

        assertThat(order.cancel(CancelReason.STOCK_UNAVAILABLE, NOW))
                .isEqualTo(Order.Transition.APPLIED);
        assertThat(order.cancelReason()).isEqualTo(CancelReason.STOCK_UNAVAILABLE);
        assertThat(order.cancel(CancelReason.PAYMENT_TIMEOUT, NOW))
                .isEqualTo(Order.Transition.ALREADY);
        assertThat(order.cancelReason()).isEqualTo(CancelReason.STOCK_UNAVAILABLE);
    }

    @Test
    @DisplayName("결제 대기 주문은 결제가 승인되면 결제 완료가 되고 승인 시각이 결제 시각이다. 다시 와도 그대로다")
    void pay() {
        Order order = Order.place(buyerId, List.of(item(sellerA, "청바지", 1_000, 2)), address, NOW);
        order.awaitPayment(NOW);
        Instant approvedAt = NOW.plusSeconds(60);

        assertThat(order.pay(2_000, approvedAt, NOW.plusSeconds(61)))
                .isEqualTo(Order.Transition.APPLIED);
        assertThat(order.status()).isEqualTo(OrderStatus.PAID);
        assertThat(order.paidAt()).isEqualTo(approvedAt);
        assertThat(order.pay(2_000, NOW.plusSeconds(120), NOW.plusSeconds(121)))
                .isEqualTo(Order.Transition.ALREADY);
        assertThat(order.paidAt()).isEqualTo(approvedAt);
        assertThat(order.cancel(CancelReason.PAYMENT_TIMEOUT, NOW))
                .isEqualTo(Order.Transition.INVALID);
    }

    @Test
    @DisplayName("결제 대기가 아닌 주문(예약 중, 취소됨)은 결제 완료가 되지 않는다. 금액이 다르면 예외다")
    void payRejected() {
        Order pending = Order.place(buyerId, List.of(item(sellerA, "청바지", 1_000, 1)), address, NOW);
        assertThat(pending.pay(1_000, NOW, NOW)).isEqualTo(Order.Transition.INVALID);
        assertThat(pending.status()).isEqualTo(OrderStatus.PENDING);

        Order cancelled =
                Order.place(buyerId, List.of(item(sellerA, "청바지", 1_000, 1)), address, NOW);
        cancelled.awaitPayment(NOW);
        cancelled.cancel(CancelReason.PAYMENT_TIMEOUT, NOW);
        assertThat(cancelled.pay(1_000, NOW, NOW)).isEqualTo(Order.Transition.INVALID);
        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelled.paidAt()).isNull();

        Order awaiting =
                Order.place(buyerId, List.of(item(sellerA, "청바지", 1_000, 1)), address, NOW);
        awaiting.awaitPayment(NOW);
        assertThatThrownBy(() -> awaiting.pay(999, NOW, NOW))
                .isInstanceOf(IllegalStateException.class);
        assertThat(awaiting.status()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
    }

    @Test
    @DisplayName("결제 전 하위 주문은 진행 상태가 없고, 결제 완료가 되면 모든 하위 주문이 PAID다")
    void progressStartsAtPayment() {
        Order order = twoSellers();
        assertThat(order.sellerOrders()).allSatisfy(s -> assertThat(s.progress()).isNull());

        order.awaitPayment(NOW);
        order.pay(order.amount(), NOW, NOW);

        assertThat(order.sellerOrders())
                .allSatisfy(s -> assertThat(s.progress()).isEqualTo(SellerOrderProgress.PAID));
    }

    @Test
    @DisplayName("하위 주문 진행 상태는 그 하위 주문만, 앞으로만 바뀐다. 같거나 뒤의 단계는 그대로다")
    void advancesSellerOrder() {
        Order order = twoSellers();
        order.awaitPayment(NOW);
        order.pay(order.amount(), NOW, NOW);
        UUID first = order.sellerOrders().get(0).id();

        assertThat(order.advanceSellerOrder(first, SellerOrderProgress.SHIPPED, NOW))
                .isEqualTo(Order.Transition.APPLIED);
        assertThat(order.advanceSellerOrder(first, SellerOrderProgress.ACCEPTED, NOW))
                .isEqualTo(Order.Transition.ALREADY);
        assertThat(order.advanceSellerOrder(first, SellerOrderProgress.SHIPPED, NOW))
                .isEqualTo(Order.Transition.ALREADY);

        assertThat(order.sellerOrders().get(0).progress()).isEqualTo(SellerOrderProgress.SHIPPED);
        assertThat(order.sellerOrders().get(1).progress()).isEqualTo(SellerOrderProgress.PAID);
    }

    @Test
    @DisplayName("결제 전 주문이나 주문에 없는 하위 주문은 진행 상태를 바꿀 수 없다")
    void advanceRejected() {
        Order order = twoSellers();
        UUID first = order.sellerOrders().get(0).id();

        assertThat(order.advanceSellerOrder(first, SellerOrderProgress.ACCEPTED, NOW))
                .isEqualTo(Order.Transition.INVALID);
        order.awaitPayment(NOW);
        order.pay(order.amount(), NOW, NOW);
        assertThat(order.advanceSellerOrder(UUID.randomUUID(), SellerOrderProgress.ACCEPTED, NOW))
                .isEqualTo(Order.Transition.INVALID);
    }

    private Order twoSellers() {
        return Order.place(
                buyerId,
                List.of(item(sellerA, "반팔 티셔츠", 19_900, 1), item(sellerB, "청바지", 39_900, 1)),
                address,
                NOW);
    }

    @Test
    @DisplayName("배송지 규칙: 받는 사람, 연락처 형식, 우편번호 5자리, 기본 주소. 어기면 ORDER_INVALID_INPUT이다")
    void shippingAddressRules() {
        assertThatThrownBy(() -> ShippingAddress.of(" ", "010-1234-5678", "06236", "주소", null))
                .extracting("code")
                .isEqualTo("ORDER_INVALID_INPUT");
        assertThatThrownBy(() -> ShippingAddress.of("홍길동", "01012345678", "06236", "주소", null))
                .extracting("code")
                .isEqualTo("ORDER_INVALID_INPUT");
        assertThatThrownBy(() -> ShippingAddress.of("홍길동", "010-1234-5678", "0623", "주소", null))
                .extracting("code")
                .isEqualTo("ORDER_INVALID_INPUT");
        assertThatThrownBy(
                        () ->
                                ShippingAddress.of(
                                        "홍길동", "010-1234-5678", "06236", "가".repeat(201), null))
                .extracting("code")
                .isEqualTo("ORDER_INVALID_INPUT");
        assertThat(
                        ShippingAddress.of(" 홍길동 ", "010-1234-5678", "06236", "주소", "  ")
                                .detailAddress())
                .isNull();
    }

    private static OrderItem item(UUID sellerId, String productName, long price, int quantity) {
        return new OrderItem(
                UUID.randomUUID(),
                UUID.randomUUID(),
                productName,
                sellerId,
                "상점",
                CATEGORY,
                List.of("색상"),
                List.of("블랙"),
                price,
                quantity);
    }
}
