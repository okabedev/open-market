package com.openmarket.settlement.command.application;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 판매자·정산 기간 하나의 정산을 확정한다.
 *
 * @param adminRun 관리자 즉시 실행이면 실행한 관리자와 실행 ID. 정기 실행이면 null
 */
public record ConfirmSettlementCommand(
        UUID sellerId, Instant periodStart, @Nullable AdminRun adminRun) {

    public ConfirmSettlementCommand(UUID sellerId, Instant periodStart) {
        this(sellerId, periodStart, null);
    }

    /** 관리자 즉시 실행 한 번. 확정한 정산의 감사 로그가 실행 요약과 같은 실행 ID를 남긴다. */
    public record AdminRun(UUID adminId, UUID runId) {}
}
