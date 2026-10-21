package com.openmarket.order.ui.web;

import com.openmarket.order.query.application.OrderQueryService;
import com.openmarket.order.query.dto.OngoingOrdersView;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 서비스 간 조회. {@code internal} scope 토큰만 부른다(common-security). gateway는 {@code /internal/**}을 넘기지
 * 않는다.
 */
@RestController
public class InternalOrderQueryController {

    private final OrderQueryService orders;

    public InternalOrderQueryController(OrderQueryService orders) {
        this.orders = orders;
    }

    /** 구매자에게 진행 중인 주문이 있는지. member 탈퇴(ACC-4)가 부른다. */
    @GetMapping("/internal/v1/buyers/{buyerId}/ongoing-orders")
    public OngoingOrdersView ongoingOrders(@PathVariable UUID buyerId) {
        return orders.ongoingOrders(buyerId);
    }
}
