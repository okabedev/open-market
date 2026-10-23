package com.openmarket.promotion.command.application;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 쿠폰 발급 중지.
 *
 * @param actorId 중지하는 사람(토큰의 {@code sub})
 * @param sellerId 판매자가 중지하면 판매자 ID(자기 쿠폰만). 없으면 관리자가 중지한다(모든 쿠폰)
 */
public record StopCouponCommand(UUID actorId, @Nullable UUID sellerId, UUID couponId) {}
