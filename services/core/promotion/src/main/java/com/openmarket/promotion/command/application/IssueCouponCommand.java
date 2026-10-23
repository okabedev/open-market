package com.openmarket.promotion.command.application;

import java.util.UUID;

/**
 * 쿠폰 내려받기(PRM-2).
 *
 * @param memberId 내려받는 회원(토큰의 {@code sub})
 */
public record IssueCouponCommand(UUID memberId, UUID couponId) {}
