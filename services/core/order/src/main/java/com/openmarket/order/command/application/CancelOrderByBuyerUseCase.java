package com.openmarket.order.command.application;

import com.openmarket.common.core.error.NotFoundException;
import com.openmarket.common.messaging.outbox.Outbox;
import com.openmarket.order.command.domain.CancelReason;
import com.openmarket.order.command.domain.Order;
import com.openmarket.order.command.domain.OrderCancelled;
import com.openmarket.order.command.domain.OrderNotCancellableException;
import com.openmarket.order.command.domain.OrderRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구매자가 결제 전 주문을 취소하고 {@code OrderCancelled}(사유 {@code BUYER_CANCELLED})를
 * 낸다(docs/architecture/order.md "만료와 결제 전 취소"). 주문 행을 잠그고 바꾼다. 이미 취소된 주문이면 사유를 바꾸지 않고 성공으로 끝난다.
 */
@Service
public class CancelOrderByBuyerUseCase {

    private final OrderRepository orders;
    private final Outbox outbox;
    private final Clock clock;

    public CancelOrderByBuyerUseCase(OrderRepository orders, Outbox outbox, Clock clock) {
        this.orders = orders;
        this.outbox = outbox;
        this.clock = clock;
    }

    /**
     * 본인 주문을 취소한다.
     *
     * @throws NotFoundException 없는 주문이거나 다른 회원의 주문이면 {@code ORDER_NOT_FOUND}
     * @throws OrderNotCancellableException 결제가 끝난 주문
     */
    @Transactional
    public void execute(CancelOrderByBuyerCommand command) {
        Order order =
                orders.findForUpdateById(command.orderId())
                        .filter(found -> found.buyerId().equals(command.buyerId()))
                        .orElseThrow(
                                () -> new NotFoundException("ORDER_NOT_FOUND", "주문을 찾을 수 없습니다."));
        switch (order.cancel(CancelReason.BUYER_CANCELLED, clock.instant())) {
            case APPLIED -> {
                orders.save(order);
                outbox.append(
                        PlaceOrderUseCase.TOPIC,
                        order.id().toString(),
                        OrderCancelled.TYPE,
                        new OrderCancelled(order.id(), CancelReason.BUYER_CANCELLED));
            }
            case ALREADY -> {}
            case INVALID -> throw new OrderNotCancellableException();
        }
    }
}
