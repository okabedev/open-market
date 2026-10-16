package com.openmarket.archgood.command.application;

import com.openmarket.archgood.command.domain.Order;
import java.util.UUID;

public class PlaceOrderUseCase {
    private final OrderPort orderPort;

    public PlaceOrderUseCase(OrderPort orderPort) {
        this.orderPort = orderPort;
    }

    public String execute(PlaceOrderCommand command) {
        Order order = new Order(UUID.randomUUID());
        orderPort.save(order);
        return order.id().toString();
    }
}
