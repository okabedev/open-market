package com.openmarket.review.ui.web;

import com.openmarket.review.command.application.ChangeReviewVisibilityCommand;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * 관리자 리뷰 숨김·보임 사유. 감사 로그에 남는다.
 *
 * @param reason 1~200자. 앞뒤 공백은 지운다
 */
public record ChangeReviewVisibilityRequest(@NotNull String reason) {

    ChangeReviewVisibilityCommand toCommand(UUID adminId, UUID reviewId, boolean visible) {
        return new ChangeReviewVisibilityCommand(adminId, reviewId, visible, reason);
    }
}
