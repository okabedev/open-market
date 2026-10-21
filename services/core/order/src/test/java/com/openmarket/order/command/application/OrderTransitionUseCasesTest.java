package com.openmarket.order.command.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmarket.order.command.domain.CancelReason;
import com.openmarket.order.command.domain.Order;
import com.openmarket.order.command.domain.OrderAwaitingPayment;
import com.openmarket.order.command.domain.OrderCancelled;
import com.openmarket.order.command.domain.OrderItem;
import com.openmarket.order.command.domain.OrderStatus;
import com.openmarket.order.command.domain.ShippingAddress;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 예약 결과로 바뀌는 주문 상태(docs/architecture/order.md "예약 결과"). */
class OrderTransitionUseCasesTest {

    static final Instant NOW = Instant.parse("2026-10-20T03:00:00Z");

    final OrderFakes.Orders orders = new OrderFakes.Orders();
    final OrderFakes.RecordingOutbox outbox = new OrderFakes.RecordingOutbox();
    final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    final AwaitPaymentUseCase awaitPayment = new AwaitPaymentUseCase(orders, outbox, clock);
    final CancelOrderUseCase cancel = new CancelOrderUseCase(orders, outbox, clock);

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
    @DisplayName("order가 모르는 주문의 결과는 넘긴다")
    void unknownOrder() {
        awaitPayment.execute(new AwaitPaymentCommand(UUID.randomUUID()));
        cancel.execute(
                new CancelOrderCommand(
                        UUID.randomUUID(), CancelOrderCommand.Reason.STOCK_UNAVAILABLE));

        assertThat(outbox.events).isEmpty();
    }
}
