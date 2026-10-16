package com.openmarket.archgood.command.domain;

import java.util.UUID;

public class Order {
    private final UUID id;
    private OrderStatus status = OrderStatus.PENDING;

    public Order(UUID id) {
        this.id = id;
    }

    public UUID id() {
        return id;
    }

    public OrderStatus status() {
        return status;
    }
}
