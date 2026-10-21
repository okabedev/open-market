package com.openmarket.order.command.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 주문 저장소. 구현은 {@code infra}에 있다. */
public interface OrderRepository {

    Order save(Order order);

    /** 주문 행을 잠그고 읽는다(상태 전이). */
    Optional<Order> findForUpdateById(UUID id);

    /**
     * 결제 전({@code PENDING}, {@code AWAITING_PAYMENT})이고 {@code placedBefore} 이전에 만든 주문. 오래된 순, 최대
     * {@code limit}개.
     */
    List<UUID> findUnpaidIdsPlacedBefore(Instant placedBefore, int limit);
}
