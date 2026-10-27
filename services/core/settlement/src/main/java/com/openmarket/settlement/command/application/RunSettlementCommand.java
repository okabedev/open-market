package com.openmarket.settlement.command.application;

import java.util.UUID;

/**
 * 관리자 즉시 정산 실행.
 *
 * @param adminId 실행한 관리자(토큰의 {@code sub}). 감사 로그에 남긴다
 */
public record RunSettlementCommand(UUID adminId) {}
