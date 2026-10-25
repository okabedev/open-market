package com.openmarket.review.ui.web;

import com.openmarket.review.command.application.WriteReviewReplyCommand;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * 판매자 답글. 처음이면 달고, 있으면 고친다.
 *
 * @param content 1~1,000자. 앞뒤 공백은 지운다
 */
public record WriteReviewReplyRequest(@NotNull String content) {

    WriteReviewReplyCommand toCommand(UUID sellerId, UUID reviewId) {
        return new WriteReviewReplyCommand(sellerId, reviewId, content);
    }
}
