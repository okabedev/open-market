package com.openmarket.order.ui.web;

import com.openmarket.order.query.application.OrderQueryService;
import com.openmarket.order.query.dto.ClaimStatus;
import com.openmarket.order.query.dto.PageView;
import com.openmarket.order.query.dto.SellerOrderDetailView;
import com.openmarket.order.query.dto.SellerOrderProgress;
import com.openmarket.order.query.dto.SellerOrderSummaryView;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
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

    /**
     * 본인 하위 주문 목록. 최근 결제 순, 쪽 번호. 진행 상태({@code progress})와 클레임 상태({@code claimStatus})로 거른다. 같은 조건을
     * 여러 번 주면 그중 하나, 둘 다 주면 둘 다 맞는 것이다.
     */
    @GetMapping
    public PageView<SellerOrderSummaryView> list(
            Principal principal,
            @RequestParam(required = false) @Nullable List<SellerOrderProgress> progress,
            @RequestParam(required = false) @Nullable List<ClaimStatus> claimStatus,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return orders.sellerOrders(
                sellerId(principal),
                progress == null ? List.of() : progress,
                claimStatus == null ? List.of() : claimStatus,
                page,
                size);
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
