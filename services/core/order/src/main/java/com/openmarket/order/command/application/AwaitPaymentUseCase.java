package com.openmarket.order.command.application;

import com.openmarket.common.messaging.outbox.Outbox;
import com.openmarket.order.command.domain.Order;
import com.openmarket.order.command.domain.OrderAwaitingPayment;
import com.openmarket.order.command.domain.OrderRepository;
import java.time.Clock;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 재고 예약이 끝난 주문을 결제 대기로 바꾸고 {@code OrderAwaitingPayment}를 낸다(docs/architecture/order.md "예약 결과"). 주문
 * 행을 잠그고 바꾼다. {@code PENDING}이 아니거나 모르는 주문이면 넘긴다.
 */
@Service
public class AwaitPaymentUseCase {

    private static final Logger log = LoggerFactory.getLogger(AwaitPaymentUseCase.class);

    private final OrderRepository orders;
    private final Outbox outbox;
    private final Clock clock;

    public AwaitPaymentUseCase(OrderRepository orders, Outbox outbox, Clock clock) {
        this.orders = orders;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public void execute(AwaitPaymentCommand command) {
        Optional<Order> found = orders.findForUpdateById(command.orderId());
        if (found.isEmpty()) {
            log.warn("모르는 주문의 예약 결과라 넘긴다: orderId={}", command.orderId());
            return;
        }
        Order order = found.get();
        switch (order.awaitPayment(clock.instant())) {
            case APPLIED -> {
                orders.save(order);
                outbox.append(
                        PlaceOrderUseCase.TOPIC,
                        order.id().toString(),
                        OrderAwaitingPayment.TYPE,
                        new OrderAwaitingPayment(
                                order.id(), order.buyerId(), order.amount(), order.orderName()));
            }
            case ALREADY -> {}
            case INVALID ->
                    log.warn(
                            "결제 대기로 바꿀 수 없는 상태라 넘긴다: orderId={}, status={}",
                            order.id(),
                            order.status());
        }
    }
}
