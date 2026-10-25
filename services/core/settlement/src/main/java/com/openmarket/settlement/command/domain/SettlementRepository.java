package com.openmarket.settlement.command.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** 정산 저장소. 구현은 {@code infra}에 있다. */
public interface SettlementRepository {

    Settlement save(Settlement settlement);

    /** 지급 처리. 같은 정산을 동시에 지급하면 한쪽이 기다린다. */
    Optional<Settlement> findForUpdateById(UUID settlementId);

    boolean existsBySellerIdAndPeriodStart(UUID sellerId, Instant periodStart);
}
