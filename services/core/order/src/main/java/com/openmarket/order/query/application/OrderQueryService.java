package com.openmarket.order.query.application;

import com.openmarket.common.core.error.NotFoundException;
import com.openmarket.order.query.dao.OrderQueryDao;
import com.openmarket.order.query.dto.OrderDetailView;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 주문 조회(ORD-3). 다른 회원의 주문은 없는 주문처럼 404 {@code ORDER_NOT_FOUND}다. */
@Service
public class OrderQueryService {

    private final OrderQueryDao dao;

    public OrderQueryService(OrderQueryDao dao) {
        this.dao = dao;
    }

    @Transactional(readOnly = true)
    public OrderDetailView buyerDetail(UUID buyerId, UUID orderId) {
        return dao.findForBuyer(buyerId, orderId)
                .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "주문을 찾을 수 없습니다."));
    }
}
