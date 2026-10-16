package com.openmarket.archgood.query.application;

import com.openmarket.archgood.query.dao.OrderQueryDao;
import com.openmarket.archgood.query.dto.OrderView;

public class OrderQueryService {
    private final OrderQueryDao dao = new OrderQueryDao();

    public OrderView get(String id) {
        return dao.find(id);
    }
}
