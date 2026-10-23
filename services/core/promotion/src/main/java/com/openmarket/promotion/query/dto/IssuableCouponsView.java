package com.openmarket.promotion.query.dto;

import java.util.List;

/** 지금 내려받을 수 있는 쿠폰 목록. 발급 기간이 먼저 끝나는 순이다. */
public record IssuableCouponsView(List<CouponView> items) {}
