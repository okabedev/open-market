package com.openmarket.archgood.ui.web;

import com.openmarket.archgood.command.application.PlaceOrderCommand;
import com.openmarket.archgood.command.application.PlaceOrderUseCase;
import com.openmarket.archgood.query.application.OrderQueryService;

public class OrderCommandController {
    private final PlaceOrderUseCase placeOrder;
    private final OrderQueryService queries = new OrderQueryService();

    public OrderCommandController(PlaceOrderUseCase placeOrder) {
        this.placeOrder = placeOrder;
    }

    public OrderResponse place(PlaceOrderRequest request) {
        String id = placeOrder.execute(new PlaceOrderCommand(request.buyerId()));
        return new OrderResponse(queries.get(id).id());
    }
}
