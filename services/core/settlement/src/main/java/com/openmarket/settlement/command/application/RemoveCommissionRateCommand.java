package com.openmarket.settlement.command.application;

import java.util.UUID;

/**
 * 관리자가 카테고리의 수수료율을 지워 기본 요율로 돌린다.
 *
 * @param adminId 토큰의 {@code sub}. 감사 로그에 남는다
 */
public record RemoveCommissionRateCommand(UUID adminId, UUID categoryId) {}
