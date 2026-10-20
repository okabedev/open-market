package com.openmarket.order.ui.web;

import static com.openmarket.order.ui.web.CartQueryController.memberId;

import com.openmarket.common.web.idempotency.Idempotent;
import com.openmarket.order.command.application.PlaceOrderCommand;
import com.openmarket.order.command.application.PlaceOrderUseCase;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 주문 생성(ORD-2). 주체는 토큰의 {@code sub}(= buyerId)다. 규칙은 docs/architecture/order.md "주문 생성". */
@RestController
public class OrderCommandController {

    private final PlaceOrderUseCase place;

    public OrderCommandController(PlaceOrderUseCase place) {
        this.place = place;
    }

    /**
     * 주문을 {@code PENDING}으로 만든다. {@code Idempotency-Key}가 필요하다. 가격이 바뀌었으면 409 {@code
     * ORDER_PRICE_CHANGED}({@code changes}), 판매 중이 아니면 422 {@code ORDER_SKU_NOT_ON_SALE}({@code
     * skuIds}), 수량이 모자라면 422 {@code ORDER_INSUFFICIENT_STOCK}({@code skuIds}).
     */
    @PostMapping("/v1/orders")
    @ResponseStatus(HttpStatus.CREATED)
    @Idempotent
    public PlaceOrderResponse place(
            Principal principal, @Valid @RequestBody PlaceOrderRequest request) {
        PlaceOrderRequest.Shipping shipping = request.shipping();
        return new PlaceOrderResponse(
                place.execute(
                        new PlaceOrderCommand(
                                memberId(principal),
                                request.lines().stream()
                                        .map(
                                                line ->
                                                        new PlaceOrderCommand.Line(
                                                                line.skuId(),
                                                                line.quantity(),
                                                                line.expectedPrice()))
                                        .toList(),
                                new PlaceOrderCommand.Shipping(
                                        shipping.recipientName(),
                                        shipping.recipientPhone(),
                                        shipping.zipCode(),
                                        shipping.baseAddress(),
                                        shipping.detailAddress()))));
    }
}
