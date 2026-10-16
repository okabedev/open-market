package com.openmarket.archgood.query.dto;

public record OrderView(String id, OrderStatus status) {
    public OrderView(String id, String status) {
        this(id, OrderStatus.valueOf(status));
    }
}
