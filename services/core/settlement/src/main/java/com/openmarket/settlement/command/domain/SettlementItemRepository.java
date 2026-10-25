package com.openmarket.settlement.command.domain;

import java.util.UUID;

/** 정산 대상 저장소. 구현은 {@code infra}에 있다. */
public interface SettlementItemRepository {

    SettlementItem save(SettlementItem item);

    boolean existsBySellerOrderId(UUID sellerOrderId);
}
