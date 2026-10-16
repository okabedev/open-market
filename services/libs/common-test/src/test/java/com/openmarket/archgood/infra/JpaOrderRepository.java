package com.openmarket.archgood.infra;

import com.openmarket.archgood.command.application.OrderPort;
import com.openmarket.archgood.command.domain.Order;
import java.util.ArrayList;
import java.util.List;

public class JpaOrderRepository implements OrderPort {
    private final List<Order> orders = new ArrayList<>();

    @Override
    public void save(Order order) {
        orders.add(order);
    }
}
