package com.openmarket.order.query.dto;

/**
 * 구매자에게 진행 중인 주문이 있는지(ACC-4). member 탈퇴가 쓴다.
 *
 * @param hasOngoingOrders 결제 전이거나 결제된 주문이 하나라도 있으면 true
 */
public record OngoingOrdersView(boolean hasOngoingOrders) {}
