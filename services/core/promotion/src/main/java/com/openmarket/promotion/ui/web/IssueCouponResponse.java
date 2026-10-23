package com.openmarket.promotion.ui.web;

import java.util.UUID;

/** 내려받은 발급 쿠폰. 새로 받았으면 201, 이미 받은 쿠폰이면 200이고 같은 ID다. */
public record IssueCouponResponse(UUID issuedCouponId) {}
