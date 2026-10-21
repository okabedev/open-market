package com.openmarket.fulfillment.ui.web;

import com.openmarket.fulfillment.query.application.FulfillmentQueryService;
import com.openmarket.fulfillment.query.dto.SellerFulfillmentView;
import java.security.Principal;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 판매자 하위 주문의 배송 조회(FUL-1). 주체는 토큰의 {@code sub}(= sellerId)다. */
@RestController
@RequestMapping("/v1/seller/seller-orders")
public class SellerFulfillmentQueryController {

    private final FulfillmentQueryService fulfillments;

    public SellerFulfillmentQueryController(FulfillmentQueryService fulfillments) {
        this.fulfillments = fulfillments;
    }

    /** 본인 하위 주문의 배송 진행. 다른 판매자의 것이거나 배송이 아직 없으면 404 {@code FULFILLMENT_NOT_FOUND}. */
    @GetMapping("/{sellerOrderId}")
    public SellerFulfillmentView detail(Principal principal, @PathVariable UUID sellerOrderId) {
        return fulfillments.sellerFulfillment(UUID.fromString(principal.getName()), sellerOrderId);
    }
}
