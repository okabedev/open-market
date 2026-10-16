package com.openmarket.archgood.ui.event;

import com.openmarket.archgood.command.application.PlaceOrderCommand;
import com.openmarket.archgood.command.application.PlaceOrderUseCase;

public class OrderPlacedListener {
    private final PlaceOrderUseCase placeOrder;

    public OrderPlacedListener(PlaceOrderUseCase placeOrder) {
        this.placeOrder = placeOrder;
    }

    public void on(String buyerId) {
        placeOrder.execute(new PlaceOrderCommand(buyerId));
    }
}
