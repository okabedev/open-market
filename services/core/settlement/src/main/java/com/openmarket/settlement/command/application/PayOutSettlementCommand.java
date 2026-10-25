package com.openmarket.settlement.command.application;

import java.util.UUID;

/**
 * 관리자가 확정된 정산을 지급 완료로 기록한다.
 *
 * @param adminId 토큰의 {@code sub}. 정산과 감사 로그에 남는다
 */
public record PayOutSettlementCommand(UUID adminId, UUID settlementId) {}
