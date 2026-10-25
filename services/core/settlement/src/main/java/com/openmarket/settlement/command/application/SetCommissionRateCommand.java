package com.openmarket.settlement.command.application;

import java.util.UUID;

/**
 * 관리자가 카테고리의 수수료율을 정한다.
 *
 * @param adminId 토큰의 {@code sub}. 감사 로그에 남는다
 * @param categoryId 소분류 카테고리(catalog)
 * @param rateBp 0~10,000(bp, 0.01%)
 */
public record SetCommissionRateCommand(UUID adminId, UUID categoryId, int rateBp) {}
