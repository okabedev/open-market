package com.openmarket.member.command.application;

import java.util.UUID;

/**
 * 진행 중인 주문·클레임이 있는지 묻는다(ACC-4, context-map "진행 중 주문 조회"). M3에서 order 내부 API(OpenFeign)로 구현한다. 그 전에는
 * 항상 없다고 답한다.
 */
public interface OngoingOrderPort {

    boolean hasOngoingOrders(UUID memberId);
}
