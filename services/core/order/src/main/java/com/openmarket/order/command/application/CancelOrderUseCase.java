package com.openmarket.order.command.application;

import com.openmarket.common.messaging.outbox.Outbox;
import com.openmarket.order.command.domain.CancelReason;
import com.openmarket.order.command.domain.Order;
import com.openmarket.order.command.domain.OrderCancelled;
import com.openmarket.order.command.domain.OrderRepository;
import java.time.Clock;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 전 주문을 취소하고 {@code OrderCancelled}를 낸다(docs/architecture/order.md "예약 결과"). catalog가 예약을 풀고
 * payment가 결제를 무효로 한다. 주문 행을 잠그고 바꾼다. 이미 취소됐거나 결제가 끝났거나 모르는 주문이면 넘긴다.
 */
@Service
public class CancelOrderUseCase {

    private static final Logger log = LoggerFactory.getLogger(CancelOrderUseCase.class);

    private final OrderRepository orders;
    private final Outbox outbox;
    private final Clock clock;

    public CancelOrderUseCase(OrderRepository orders, Outbox outbox, Clock clock) {
        this.orders = orders;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public void execute(CancelOrderCommand command) {
        Optional<Order> found = orders.findForUpdateById(command.orderId());
        if (found.isEmpty()) {
            log.warn("모르는 주문의 취소라 넘긴다: orderId={}", command.orderId());
            return;
        }
        Order order = found.get();
        CancelReason reason = CancelReason.valueOf(command.reason().name());
        switch (order.cancel(reason, clock.instant())) {
            case APPLIED -> {
                orders.save(order);
                outbox.append(
                        PlaceOrderUseCase.TOPIC,
                        order.id().toString(),
                        OrderCancelled.TYPE,
                        new OrderCancelled(order.id(), reason));
            }
            case ALREADY -> {}
            case INVALID ->
                    log.warn("취소할 수 없는 상태라 넘긴다: orderId={}, status={}", order.id(), order.status());
        }
    }
}
