package com.openmarket.order.ui.web;

import com.openmarket.order.query.application.OrderQueryService;
import com.openmarket.order.query.dto.PageView;
import com.openmarket.order.query.dto.SellerOrderDetailView;
import com.openmarket.order.query.dto.SellerOrderSummaryView;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.security.Principal;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 판매자 본인 하위 주문 조회(ORD-4). 주체는 토큰의 {@code sub}(= sellerId)다. 결제된 주문만 보인다. */
@RestController
@RequestMapping("/v1/seller/orders")
public class SellerOrderQueryController {

    private final OrderQueryService orders;

    public SellerOrderQueryController(OrderQueryService orders) {
        this.orders = orders;
    }

    /** 본인 하위 주문 목록. 최근 주문 순, 쪽 번호. */
    @GetMapping
    public PageView<SellerOrderSummaryView> list(
            Principal principal,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return orders.sellerOrders(sellerId(principal), page, size);
    }

    /** 본인 하위 주문 상세. 다른 판매자의 것이거나 결제 전이면 404 {@code ORDER_NOT_FOUND}. */
    @GetMapping("/{sellerOrderId}")
    public SellerOrderDetailView detail(Principal principal, @PathVariable UUID sellerOrderId) {
        return orders.sellerDetail(sellerId(principal), sellerOrderId);
    }

    private static UUID sellerId(Principal principal) {
        return UUID.fromString(principal.getName());
    }
}
