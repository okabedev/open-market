package com.openmarket.order.command.application;

import com.openmarket.order.command.domain.Order;
import com.openmarket.order.command.domain.OrderRepository;
import com.openmarket.order.command.domain.SellerOrderProgress;
import java.time.Clock;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 하위 주문 진행 상태를 앞으로 바꾼다(docs/architecture/order.md "하위 주문 진행 상태"). 주문 행을 잠그고 바꾸며 이벤트는 내지 않는다. 같거나 뒤의
 * 단계는 조용히, 모르는 주문·하위 주문이나 결제 전 주문은 경고 로그를 남기고 넘긴다.
 */
@Service
public class AdvanceSellerOrderUseCase {

    private static final Logger log = LoggerFactory.getLogger(AdvanceSellerOrderUseCase.class);

    private final OrderRepository orders;
    private final Clock clock;

    public AdvanceSellerOrderUseCase(OrderRepository orders, Clock clock) {
        this.orders = orders;
        this.clock = clock;
    }

    @Transactional
    public void execute(AdvanceSellerOrderCommand command) {
        Optional<Order> found = orders.findForUpdateById(command.orderId());
        if (found.isEmpty()) {
            log.warn("모르는 주문의 배송 진행이라 넘긴다: orderId={}", command.orderId());
            return;
        }
        Order order = found.get();
        SellerOrderProgress progress = SellerOrderProgress.valueOf(command.stage().name());
        switch (order.advanceSellerOrder(command.sellerOrderId(), progress, clock.instant())) {
            case APPLIED -> orders.save(order);
            case ALREADY -> {}
            case INVALID ->
                    log.warn(
                            "하위 주문 진행 상태를 바꿀 수 없어 넘긴다: orderId={}, sellerOrderId={}, status={}",
                            order.id(),
                            command.sellerOrderId(),
                            order.status());
        }
    }
}
