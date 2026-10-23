package com.openmarket.review.ui.web;

import com.openmarket.review.command.application.WriteReviewCommand;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 리뷰 작성. 규칙은 docs/architecture/review.md "리뷰". 쓴 뒤 고치거나 지울 수 없다.
 *
 * @param orderLineId 구매확정한 내 주문 항목
 * @param rating 1~5
 * @param content 10~1,000자. 앞뒤 공백은 지운다
 * @param fileIds 리뷰 사진({@code REVIEW_IMAGE}로 올린 파일) 0~5장, 중복 없이. 없으면 빈 목록
 */
public record WriteReviewRequest(
        @NotNull UUID orderLineId,
        int rating,
        @NotNull String content,
        @Nullable List<UUID> fileIds) {

    WriteReviewCommand toCommand(UUID buyerId) {
        return new WriteReviewCommand(
                buyerId,
                orderLineId,
                rating,
                content,
                List.copyOf(Objects.requireNonNullElse(fileIds, List.of())));
    }
}
