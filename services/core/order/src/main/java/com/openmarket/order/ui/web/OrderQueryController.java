package com.openmarket.order.ui.web;

import static com.openmarket.order.ui.web.CartQueryController.memberId;

import com.openmarket.order.query.application.OrderQueryService;
import com.openmarket.order.query.dto.CursorPageView;
import com.openmarket.order.query.dto.OrderDetailView;
import com.openmarket.order.query.dto.OrderSummaryView;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.security.Principal;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 구매자 주문 조회(ORD-3). 주체는 토큰의 {@code sub}(= buyerId)다. */
@RestController
public class OrderQueryController {

    private final OrderQueryService orders;

    public OrderQueryController(OrderQueryService orders) {
        this.orders = orders;
    }

    /**
     * 본인 주문 목록. 모든 상태를 최근 주문 순으로 준다. 다음 쪽은 응답의 {@code nextCursor}를 {@code cursor}로 넘긴다. 커서가 틀리면 400
     * {@code ORDER_INVALID_CURSOR}.
     */
    @GetMapping("/v1/orders")
    public CursorPageView<OrderSummaryView> orders(
            Principal principal,
            @RequestParam(required = false) @Nullable String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return orders.buyerOrders(memberId(principal), cursor, size);
    }

    /** 본인 주문 상세. 다른 회원의 주문은 404 {@code ORDER_NOT_FOUND}. 주문을 만든 뒤 결제 대기가 될 때까지 이 API로 기다린다. */
    @GetMapping("/v1/orders/{orderId}")
    public OrderDetailView order(Principal principal, @PathVariable UUID orderId) {
        return orders.buyerDetail(memberId(principal), orderId);
    }
}
