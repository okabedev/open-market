package com.openmarket.archgood.query.dao;

import com.openmarket.archgood.command.domain.QOrder;
import com.openmarket.archgood.query.dto.OrderView;

public class OrderQueryDao {
    public OrderView find(String id) {
        String column = QOrder.order.id.getMetadata().getName();
        return new OrderView(id + column, "PENDING");
    }
}
