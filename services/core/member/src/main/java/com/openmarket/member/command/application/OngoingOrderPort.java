package com.openmarket.member.command.application;

import java.util.UUID;

/**
 * 진행 중인 주문·클레임이 있는지 묻는다(ACC-4, context-map "진행 중 주문 조회"). {@code infra}가 order 내부 API(OpenFeign)로
 * 구현한다. order를 부를 수 없으면 503 {@code MEMBER_ORDER_UNAVAILABLE}을 던진다.
 */
public interface OngoingOrderPort {

    boolean hasOngoingOrders(UUID memberId);
}
