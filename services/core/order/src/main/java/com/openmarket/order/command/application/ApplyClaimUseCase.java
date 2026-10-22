package com.openmarket.order.command.application;

import com.openmarket.order.command.domain.ClaimStatus;
import com.openmarket.order.command.domain.ClaimType;
import com.openmarket.order.command.domain.Order;
import com.openmarket.order.command.domain.OrderRepository;
import java.time.Clock;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 하위 주문 클레임을 바꾼다(docs/architecture/order.md "하위 주문 클레임"). 발송 전 취소가 끝나면 진행 상태가 {@code CANCELLED}가
 * 된다. 주문 행을 잠그고 바꾸며 이벤트는 내지 않는다. 같은 상태는 조용히, 모르는 주문·하위 주문이나 결제 전 주문은 경고 로그를 남기고 넘긴다.
 */
@Service
public class ApplyClaimUseCase {

    private static final Logger log = LoggerFactory.getLogger(ApplyClaimUseCase.class);

    private final OrderRepository orders;
    private final Clock clock;

    public ApplyClaimUseCase(OrderRepository orders, Clock clock) {
        this.orders = orders;
        this.clock = clock;
    }

    @Transactional
    public void execute(ApplyClaimCommand command) {
        Optional<Order> found = orders.findForUpdateById(command.orderId());
        if (found.isEmpty()) {
            log.warn("모르는 주문의 클레임이라 넘긴다: orderId={}", command.orderId());
            return;
        }
        Order order = found.get();
        switch (order.applyClaim(
                command.sellerOrderId(),
                command.claimId(),
                ClaimType.valueOf(command.type().name()),
                ClaimStatus.valueOf(command.status().name()),
                clock.instant())) {
            case APPLIED -> orders.save(order);
            case ALREADY -> {}
            case INVALID ->
                    log.warn(
                            "하위 주문 클레임을 바꿀 수 없어 넘긴다: orderId={}, sellerOrderId={}, status={}",
                            order.id(),
                            command.sellerOrderId(),
                            order.status());
        }
    }
}
