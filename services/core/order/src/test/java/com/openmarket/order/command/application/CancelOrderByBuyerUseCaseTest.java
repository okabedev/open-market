package com.openmarket.order.command.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.order.command.domain.CancelReason;
import com.openmarket.order.command.domain.Order;
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

/** 결제 전 구매자 취소(docs/architecture/order.md "결제 전 취소"). */
class CancelOrderByBuyerUseCaseTest {

    static final Instant NOW = Instant.parse("2026-10-21T03:00:00Z");

    final OrderFakes.Orders orders = new OrderFakes.Orders();
    final OrderFakes.RecordingOutbox outbox = new OrderFakes.RecordingOutbox();
    final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    final CancelOrderByBuyerUseCase cancel = new CancelOrderByBuyerUseCase(orders, outbox, clock);
    final CancelOrderUseCase cancelBySystem = new CancelOrderUseCase(orders, outbox, clock);

    final UUID buyerId = UUID.randomUUID();
    final Order order =
            orders.save(
                    Order.place(
                            buyerId,
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
                                            1)),
                            ShippingAddress.of("홍길동", "010-1234-5678", "06236", "주소", null),
                            NOW));

    @Test
    @DisplayName("결제 전 주문을 BUYER_CANCELLED로 취소하고 OrderCancelled를 낸다. 다시 취소해도 한 번만 낸다")
    void cancelsBeforePayment() {
        order.recordReservation(Order.Reservation.STOCK, NOW);

        cancel.execute(new CancelOrderByBuyerCommand(buyerId, order.id()));
        cancel.execute(new CancelOrderByBuyerCommand(buyerId, order.id()));

        assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.cancelReason()).isEqualTo(CancelReason.BUYER_CANCELLED);
        assertThat(outbox.events)
                .singleElement()
                .satisfies(
                        e ->
                                assertThat(e.payload())
                                        .isEqualTo(
                                                new OrderCancelled(
                                                        order.id(), CancelReason.BUYER_CANCELLED)));
    }

    @Test
    @DisplayName("이미 다른 사유로 취소된 주문은 사유를 바꾸지 않고 성공으로 끝난다")
    void alreadyCancelled() {
        cancelBySystem.execute(
                new CancelOrderCommand(order.id(), CancelOrderCommand.Reason.PAYMENT_TIMEOUT));

        cancel.execute(new CancelOrderByBuyerCommand(buyerId, order.id()));

        assertThat(order.cancelReason()).isEqualTo(CancelReason.PAYMENT_TIMEOUT);
        assertThat(outbox.types()).containsExactly(OrderCancelled.TYPE);
    }

    @Test
    @DisplayName("다른 회원의 주문이나 없는 주문은 404 ORDER_NOT_FOUND이고 바꾸지 않는다")
    void othersOrder() {
        for (CancelOrderByBuyerCommand command :
                List.of(
                        new CancelOrderByBuyerCommand(UUID.randomUUID(), order.id()),
                        new CancelOrderByBuyerCommand(buyerId, UUID.randomUUID()))) {
            assertThatThrownBy(() -> cancel.execute(command))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            e -> assertThat(e.getCode()).isEqualTo("ORDER_NOT_FOUND"));
        }
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(outbox.events).isEmpty();
    }
}
