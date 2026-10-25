package com.openmarket.settlement.infra.persistence;

import com.openmarket.settlement.command.domain.SellerPeriod;
import com.openmarket.settlement.command.domain.SettlementItem;
import com.openmarket.settlement.command.domain.SettlementItemRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** {@link SettlementItemRepository}의 Spring Data JPA 구현. */
public interface JpaSettlementItemRepository
        extends Repository<SettlementItem, UUID>, SettlementItemRepository {

    /** 인덱스 {@code settlement_item_unsettled_idx}. */
    @Override
    @Query(
            "select i from SettlementItem i where i.sellerId = :sellerId"
                    + " and i.periodStart = :periodStart and i.settlementId is null"
                    + " order by i.confirmedAt, i.id")
    List<SettlementItem> findUnsettled(
            @Param("sellerId") UUID sellerId, @Param("periodStart") Instant periodStart);

    @Override
    default List<SellerPeriod> findClosablePeriods(Instant closedBy, int limit) {
        return findClosablePeriods(closedBy, Limit.of(limit));
    }

    /** 인덱스 {@code settlement_item_closable_idx}. */
    @Query(
            "select distinct new com.openmarket.settlement.command.domain.SellerPeriod("
                    + "i.sellerId, i.periodStart, i.periodEnd) from SettlementItem i"
                    + " where i.settlementId is null and i.periodEnd <= :closedBy"
                    + " order by i.periodStart, i.sellerId")
    List<SellerPeriod> findClosablePeriods(@Param("closedBy") Instant closedBy, Limit limit);
}
