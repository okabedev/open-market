package com.openmarket.order.command.domain;

import java.util.Optional;
import java.util.UUID;

/** 주문 저장소. 구현은 {@code infra}에 있다. */
public interface OrderRepository {

    Order save(Order order);

    /** 주문 행을 잠그고 읽는다(상태 전이). */
    Optional<Order> findForUpdateById(UUID id);
}
