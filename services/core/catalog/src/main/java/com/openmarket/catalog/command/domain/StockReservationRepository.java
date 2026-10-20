package com.openmarket.catalog.command.domain;

import java.util.Optional;
import java.util.UUID;

/** 재고 예약 저장소. 구현은 {@code infra}에 있다. */
public interface StockReservationRepository {

    StockReservation save(StockReservation reservation);

    boolean existsByOrderId(UUID orderId);

    /** 예약 행을 잠그고 읽는다(확정·해제). */
    Optional<StockReservation> findForUpdateByOrderId(UUID orderId);
}
