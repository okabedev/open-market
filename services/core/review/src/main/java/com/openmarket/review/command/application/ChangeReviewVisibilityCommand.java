package com.openmarket.review.command.application;

import java.util.UUID;

/**
 * 관리자 리뷰 숨김·보임.
 *
 * @param adminId 토큰의 {@code sub}. 감사 로그에 남는다
 * @param visible true면 다시 보이고, false면 숨긴다
 * @param reason 1~200자. 앞뒤 공백은 지운다
 */
public record ChangeReviewVisibilityCommand(
        UUID adminId, UUID reviewId, boolean visible, String reason) {}
