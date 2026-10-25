package com.openmarket.settlement.query.dto;

import java.util.List;

/**
 * 수수료율 목록. 목록에 없는 카테고리는 기본 요율이다.
 *
 * @param defaultRateBp 기본 요율(bp). 설정 {@code openmarket.settlement.default-commission-rate}
 * @param rates 요율을 정한 카테고리. 카테고리 ID 순
 */
public record CommissionRatesView(int defaultRateBp, List<CommissionRateView> rates) {}
