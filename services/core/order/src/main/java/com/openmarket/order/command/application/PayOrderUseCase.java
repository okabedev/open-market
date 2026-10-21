package com.openmarket.order.command.application;

import com.openmarket.common.messaging.outbox.Outbox;
import com.openmarket.order.command.domain.CartRepository;
import com.openmarket.order.command.domain.Order;
import com.openmarket.order.command.domain.OrderLine;
import com.openmarket.order.command.domain.OrderPaid;
import com.openmarket.order.command.domain.OrderRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제가 승인된 주문을 결제 완료로 바꾸고 {@code OrderPaid}를 낸다. 같은 트랜잭션에서 구매자 장바구니의 주문한 SKU를 뺀다
 * (docs/architecture/order.md "결제 결과"). 주문 행을 잠그고 바꾼다. 결제 대기가 아니거나 모르는 주문이면 넘긴다. 취소된 주문의 결제는
 * payment가 보상 취소한다.
 */
@Service
public class PayOrderUseCase {

    private static final Logger log = LoggerFactory.getLogger(PayOrderUseCase.class);

    private final OrderRepository orders;
    private final CartRepository carts;
    private final Outbox outbox;
    private final Clock clock;

    public PayOrderUseCase(
            OrderRepository orders, CartRepository carts, Outbox outbox, Clock clock) {
        this.orders = orders;
        this.carts = carts;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public void execute(PayOrderCommand command) {
        Optional<Order> found = orders.findForUpdateById(command.orderId());
        if (found.isEmpty()) {
            log.warn("모르는 주문의 결제 승인이라 넘긴다: orderId={}", command.orderId());
            return;
        }
        Order order = found.get();
        Instant now = clock.instant();
        switch (order.pay(command.amount(), command.approvedAt(), now)) {
            case APPLIED -> {
                orders.save(order);
                outbox.append(
                        PlaceOrderUseCase.TOPIC,
                        order.id().toString(),
                        OrderPaid.TYPE,
                        OrderPaid.of(order));
                removeFromCart(order, now);
            }
            case ALREADY -> {}
            case INVALID ->
                    log.warn(
                            "결제 완료로 바꿀 수 없는 상태라 넘긴다: orderId={}, status={}",
                            order.id(),
                            order.status());
        }
    }

    /** 주문한 SKU를 장바구니에서 뺀다. 수량이 달라도 줄을 뺀다. 장바구니가 없으면 아무것도 하지 않는다. */
    private void removeFromCart(Order order, Instant now) {
        carts.findForUpdateByMemberId(order.buyerId())
                .ifPresent(
                        cart -> {
                            for (OrderLine line : order.lines()) {
                                cart.remove(line.skuId(), now);
                            }
                            carts.save(cart);
                        });
    }
}
