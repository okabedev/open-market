package com.openmarket.order.query.application;

import com.openmarket.common.core.error.NotFoundException;
import com.openmarket.order.query.dao.OrderQueryDao;
import com.openmarket.order.query.dto.CursorPageView;
import com.openmarket.order.query.dto.OngoingOrdersView;
import com.openmarket.order.query.dto.OrderDetailView;
import com.openmarket.order.query.dto.OrderStatus;
import com.openmarket.order.query.dto.OrderSummaryView;
import com.openmarket.order.query.dto.PageView;
import com.openmarket.order.query.dto.SellerOrderDetailView;
import com.openmarket.order.query.dto.SellerOrderSummaryView;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 주문 조회(ORD-3, ORD-4). 다른 회원의 주문, 다른 판매자의 하위 주문은 없는 주문처럼 404 {@code ORDER_NOT_FOUND}다. */
@Service
public class OrderQueryService {

    /** 판매자가 보는 주문 상태. 결제 전 주문은 판매자가 처리할 것이 없어 보이지 않는다. */
    static final Set<OrderStatus> SELLER_VISIBLE = Set.of(OrderStatus.PAID);

    /**
     * 진행 중인 주문 상태(ACC-4). 결제 전 주문은 결제가 진행 중일 수 있고, 결제된 주문은 배송 전이다. M4에서 배송·클레임이 생기면 구매확정이나 클레임 완료로
     * 끝난 하위 주문을 빼도록 다시 정한다.
     */
    static final Set<OrderStatus> ONGOING =
            Set.of(OrderStatus.PENDING, OrderStatus.AWAITING_PAYMENT, OrderStatus.PAID);

    private final OrderQueryDao dao;

    public OrderQueryService(OrderQueryDao dao) {
        this.dao = dao;
    }

    @Transactional(readOnly = true)
    public OrderDetailView buyerDetail(UUID buyerId, UUID orderId) {
        return dao.findForBuyer(buyerId, orderId).orElseThrow(OrderQueryService::notFound);
    }

    @Transactional(readOnly = true)
    public CursorPageView<OrderSummaryView> buyerOrders(
            UUID buyerId, @Nullable String cursor, int size) {
        return dao.findBuyerOrders(buyerId, cursor, size);
    }

    @Transactional(readOnly = true)
    public PageView<SellerOrderSummaryView> sellerOrders(UUID sellerId, int page, int size) {
        return dao.findSellerOrders(sellerId, SELLER_VISIBLE, page, size);
    }

    @Transactional(readOnly = true)
    public SellerOrderDetailView sellerDetail(UUID sellerId, UUID sellerOrderId) {
        return dao.findForSeller(sellerId, sellerOrderId, SELLER_VISIBLE)
                .orElseThrow(OrderQueryService::notFound);
    }

    @Transactional(readOnly = true)
    public OngoingOrdersView ongoingOrders(UUID buyerId) {
        return new OngoingOrdersView(dao.existsForBuyer(buyerId, ONGOING));
    }

    private static NotFoundException notFound() {
        return new NotFoundException("ORDER_NOT_FOUND", "주문을 찾을 수 없습니다.");
    }
}
