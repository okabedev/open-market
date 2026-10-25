package com.openmarket.settlement.command.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 관리자가 정산을 지급 완료로 기록했다(docs/architecture/context-map.md "settlement.settlement"). */
public record SettlementPaidOut(UUID settlementId, UUID sellerId, long amount, Instant paidOutAt) {

    public static final String TYPE = "SettlementPaidOut";

    public static SettlementPaidOut of(Settlement settlement) {
        return new SettlementPaidOut(
                settlement.id(),
                settlement.sellerId(),
                settlement.amount(),
                Objects.requireNonNull(settlement.paidOutAt(), "지급 처리한 정산이다"));
    }
}
