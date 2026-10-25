package com.openmarket.settlement.command.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 정산 대상 저장소. 구현은 {@code infra}에 있다. */
public interface SettlementItemRepository {

    SettlementItem save(SettlementItem item);

    boolean existsBySellerOrderId(UUID sellerOrderId);

    /** 판매자·기간의 확정되지 않은 대상. */
    List<SettlementItem> findUnsettled(UUID sellerId, Instant periodStart);

    /** 끝이 {@code closedBy} 이전인 기간 중 확정되지 않은 대상이 있는 판매자·기간. 기간 시작, 판매자 순. */
    List<SellerPeriod> findClosablePeriods(Instant closedBy, int limit);
}
