package com.openmarket.catalog.command.domain;

import java.util.UUID;

/** 재고 조정 이력 저장소. 구현은 {@code infra}에 있다. 조회는 {@code query.dao}가 한다. */
public interface StockAdjustmentRepository {

    StockAdjustment save(StockAdjustment adjustment);

    /** 이 클레임을 재입고한 이력이 있는가. */
    boolean existsByClaimId(UUID claimId);
}
