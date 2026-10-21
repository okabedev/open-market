package com.openmarket.order.command.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmarket.order.command.domain.CancelReason;
import com.openmarket.order.command.domain.Cart;
import com.openmarket.order.command.domain.CartItem;
import com.openmarket.order.command.domain.Order;
import com.openmarket.order.command.domain.OrderAwaitingPayment;
import com.openmarket.order.command.domain.OrderCancelled;
import com.openmarket.order.command.domain.OrderItem;
import com.openmarket.order.command.domain.OrderPaid;
import com.openmarket.order.command.domain.OrderStatus;
import com.openmarket.order.command.domain.SellerOrderProgress;
import com.openmarket.order.command.domain.ShippingAddress;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 예약 결과와 결제 결과로 바뀌는 주문 상태(docs/architecture/order.md "예약 결과", "결제 결과"). */
class OrderTransitionUseCasesTest {

    static final Instant NOW = Instant.parse("2026-10-20T03:00:00Z");

    final OrderFakes.Orders orders = new OrderFakes.Orders();
    final OrderFakes.RecordingOutbox outbox = new OrderFakes.RecordingOutbox();
    final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    final AwaitPaymentUseCase awaitPayment = new AwaitPaymentUseCase(orders, outbox, clock);
    final CancelOrderUseCase cancel = new CancelOrderUseCase(orders, outbox, clock);
    final FakeCartRepository carts = new FakeCartRepository();
    final PayOrderUseCase pay = new PayOrderUseCase(orders, carts, outbox, clock);
    final AdvanceSellerOrderUseCase advance = new AdvanceSellerOrderUseCase(orders, clock);

    final Order order =
            orders.save(
                    Order.place(
                            UUID.randomUUID(),
                            List.of(
                                    new OrderItem(
                                            UUID.randomUUID(),
                                            UUID.randomUUID(),
                                            "반팔 티셔츠",
                                            UUID.randomUUID(),
                                            "길동상회",
                                            UUID.randomUUID(),
                                            List.of(),
                                            List.of(),
                                            19_900,
                                            3)),
                            ShippingAddress.of("홍길동", "010-1234-5678", "06236", "주소", null),
                            NOW));

    @Test
    @DisplayName("예약이 끝나면 결제 대기가 되고 구매자, 금액, 주문명을 담아 OrderAwaitingPayment를 낸다. 다시 와도 한 번만 낸다")
    void awaitsPayment() {
        awaitPayment.execute(new AwaitPaymentCommand(order.id()));
        awaitPayment.execute(new AwaitPaymentCommand(order.id()));

        assertThat(order.status()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
        assertThat(outbox.events)
                .singleElement()
                .satisfies(
                        e -> {
                            assertThat(e.aggregateId()).isEqualTo(order.id().toString());
                            assertThat(e.payload())
                                    .isEqualTo(
                                            new OrderAwaitingPayment(
                                                    order.id(), order.buyerId(), 59_700, "반팔 티셔츠"));
                        });
    }

    @Test
    @DisplayName("예약에 실패하면 STOCK_UNAVAILABLE로 취소하고 OrderCancelled를 낸다. 취소된 주문은 결제 대기가 되지 않는다")
    void cancelsOnReservationFailure() {
        cancel.execute(
                new CancelOrderCommand(order.id(), CancelOrderCommand.Reason.STOCK_UNAVAILABLE));
        cancel.execute(
                new CancelOrderCommand(order.id(), CancelOrderCommand.Reason.STOCK_UNAVAILABLE));
        awaitPayment.execute(new AwaitPaymentCommand(order.id()));

        assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.cancelReason()).isEqualTo(CancelReason.STOCK_UNAVAILABLE);
        assertThat(outbox.events)
                .singleElement()
                .satisfies(
                        e ->
                                assertThat(e.payload())
                                        .isEqualTo(
                                                new OrderCancelled(
                                                        order.id(),
                                                        CancelReason.STOCK_UNAVAILABLE)));
    }

    @Test
    @DisplayName("결제가 승인되면 결제 완료가 되고 OrderPaid를 낸다. 장바구니에서 주문한 SKU만 뺀다. 다시 와도 한 번만 한다")
    void pays() {
        UUID ordered = order.lines().getFirst().skuId();
        UUID other = UUID.randomUUID();
        Cart cart = Cart.create(order.buyerId(), NOW);
        cart.add(ordered, 1, NOW);
        cart.add(other, 2, NOW);
        carts.save(cart);
        awaitPayment.execute(new AwaitPaymentCommand(order.id()));
        Instant approvedAt = NOW.plusSeconds(90);

        pay.execute(new PayOrderCommand(order.id(), 59_700, approvedAt));
        pay.execute(new PayOrderCommand(order.id(), 59_700, approvedAt));

        assertThat(order.status()).isEqualTo(OrderStatus.PAID);
        assertThat(cart.items()).extracting(CartItem::skuId).containsExactly(other);
        assertThat(outbox.types()).containsExactly(OrderAwaitingPayment.TYPE, OrderPaid.TYPE);
        OrderPaid paid = (OrderPaid) outbox.events.getLast().payload();
        assertThat(outbox.events.getLast().aggregateId()).isEqualTo(order.id().toString());
        assertThat(paid.orderId()).isEqualTo(order.id());
        assertThat(paid.buyerId()).isEqualTo(order.buyerId());
        assertThat(paid.amount()).isEqualTo(59_700);
        assertThat(paid.paidAt()).isEqualTo(approvedAt);
        assertThat(paid.sellerOrders())
                .singleElement()
                .satisfies(
                        s -> {
                            assertThat(s.sellerOrderId())
                                    .isEqualTo(order.sellerOrders().getFirst().id());
                            assertThat(s.amount()).isEqualTo(59_700);
                            assertThat(s.lines())
                                    .singleElement()
                                    .satisfies(
                                            l -> {
                                                assertThat(l.orderLineId())
                                                        .isEqualTo(order.lines().getFirst().id());
                                                assertThat(l.skuId()).isEqualTo(ordered);
                                                assertThat(l.productName()).isEqualTo("반팔 티셔츠");
                                                assertThat(l.optionValues()).isEmpty();
                                                assertThat(l.unitPrice()).isEqualTo(19_900);
                                                assertThat(l.quantity()).isEqualTo(3);
                                                assertThat(l.amount()).isEqualTo(59_700);
                                            });
                        });
        assertThat(paid.shippingAddress().recipientName()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("장바구니가 없는 구매자도 결제 완료가 된다")
    void paysWithoutCart() {
        awaitPayment.execute(new AwaitPaymentCommand(order.id()));

        pay.execute(new PayOrderCommand(order.id(), 59_700, NOW));

        assertThat(order.status()).isEqualTo(OrderStatus.PAID);
        assertThat(outbox.types()).containsExactly(OrderAwaitingPayment.TYPE, OrderPaid.TYPE);
    }

    @Test
    @DisplayName("취소된 주문에 온 결제 승인은 넘긴다. 장바구니도 그대로다(결제는 payment가 보상 취소한다)")
    void ignoresApprovalForCancelledOrder() {
        Cart cart = Cart.create(order.buyerId(), NOW);
        cart.add(order.lines().getFirst().skuId(), 1, NOW);
        carts.save(cart);
        awaitPayment.execute(new AwaitPaymentCommand(order.id()));
        cancel.execute(
                new CancelOrderCommand(order.id(), CancelOrderCommand.Reason.PAYMENT_TIMEOUT));

        pay.execute(new PayOrderCommand(order.id(), 59_700, NOW));

        assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cart.items()).hasSize(1);
        assertThat(outbox.types()).containsExactly(OrderAwaitingPayment.TYPE, OrderCancelled.TYPE);
    }

    @Test
    @DisplayName("order가 모르는 주문의 결과는 넘긴다")
    void unknownOrder() {
        awaitPayment.execute(new AwaitPaymentCommand(UUID.randomUUID()));
        cancel.execute(
                new CancelOrderCommand(
                        UUID.randomUUID(), CancelOrderCommand.Reason.STOCK_UNAVAILABLE));
        pay.execute(new PayOrderCommand(UUID.randomUUID(), 59_700, NOW));

        assertThat(outbox.events).isEmpty();
    }

    @Test
    @DisplayName("배송 이벤트로 하위 주문 진행 상태를 앞으로 바꾸고 이벤트는 내지 않는다. 모르는 주문은 넘긴다")
    void advancesSellerOrder() {
        awaitPayment.execute(new AwaitPaymentCommand(order.id()));
        pay.execute(new PayOrderCommand(order.id(), 59_700, NOW));
        UUID sellerOrderId = order.sellerOrders().getFirst().id();

        advance.execute(
                new AdvanceSellerOrderCommand(
                        order.id(), sellerOrderId, AdvanceSellerOrderCommand.Stage.IN_TRANSIT));
        advance.execute(
                new AdvanceSellerOrderCommand(
                        order.id(), sellerOrderId, AdvanceSellerOrderCommand.Stage.SHIPPED));
        advance.execute(
                new AdvanceSellerOrderCommand(
                        UUID.randomUUID(), sellerOrderId, AdvanceSellerOrderCommand.Stage.SHIPPED));

        assertThat(order.sellerOrders().getFirst().progress())
                .isEqualTo(SellerOrderProgress.IN_TRANSIT);
        assertThat(outbox.types()).containsExactly(OrderAwaitingPayment.TYPE, OrderPaid.TYPE);
    }
}
