package com.openmarket.archgood.command.application;

import com.openmarket.archgood.command.domain.Order;

public interface OrderPort {
    void save(Order order);
}
