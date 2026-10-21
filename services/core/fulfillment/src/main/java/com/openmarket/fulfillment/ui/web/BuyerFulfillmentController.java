package com.openmarket.fulfillment.ui.web;

import com.openmarket.fulfillment.command.application.ConfirmPurchaseCommand;
import com.openmarket.fulfillment.command.application.ConfirmPurchaseUseCase;
import com.openmarket.fulfillment.query.application.FulfillmentQueryService;
import com.openmarket.fulfillment.query.dto.BuyerFulfillmentView;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 구매자 하위 주문의 배송 조회와 구매확정(FUL-2, FUL-3). 주체는 토큰의 {@code sub}(= buyerId)다. 구매확정은 다시 보내도 결과가 같아 {@code
 * Idempotency-Key}를 받지 않는다.
 */
@RestController
@RequestMapping("/v1/seller-orders/{sellerOrderId}")
public class BuyerFulfillmentController {

    private final FulfillmentQueryService fulfillments;
    private final ConfirmPurchaseUseCase confirm;

    public BuyerFulfillmentController(
            FulfillmentQueryService fulfillments, ConfirmPurchaseUseCase confirm) {
        this.fulfillments = fulfillments;
        this.confirm = confirm;
    }

    /** 본인 하위 주문의 배송. 남의 것이거나 배송이 아직 없으면 404 {@code FULFILLMENT_NOT_FOUND}. */
    @GetMapping
    public BuyerFulfillmentView detail(Principal principal, @PathVariable UUID sellerOrderId) {
        return fulfillments.buyerFulfillment(buyerId(principal), sellerOrderId);
    }

    /** 구매확정. 이미 확정했으면 그대로 204, 배송 완료 전이면 409 {@code FULFILLMENT_INVALID_STATE}. */
    @PostMapping("/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirm(Principal principal, @PathVariable UUID sellerOrderId) {
        confirm.execute(new ConfirmPurchaseCommand(buyerId(principal), sellerOrderId));
    }

    private static UUID buyerId(Principal principal) {
        return UUID.fromString(principal.getName());
    }
}
