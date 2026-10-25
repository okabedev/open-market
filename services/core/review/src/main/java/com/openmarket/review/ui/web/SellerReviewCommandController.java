package com.openmarket.review.ui.web;

import com.openmarket.review.command.application.WriteReviewReplyUseCase;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 판매자 답글(REV-3). 주체는 토큰의 {@code sub}(= 판매자 ID)다. */
@RestController
@RequestMapping("/v1/seller/reviews")
public class SellerReviewCommandController {

    private final WriteReviewReplyUseCase reply;

    public SellerReviewCommandController(WriteReviewReplyUseCase reply) {
        this.reply = reply;
    }

    /**
     * 자기 상품 리뷰에 답글을 달거나 고친다. 같은 요청을 다시 보내도 결과가 같다(204). 없거나 다른 판매자의 리뷰면 404 {@code
     * REVIEW_NOT_FOUND}, 본문 규칙 위반은 400 {@code REVIEW_INVALID_INPUT}.
     */
    @PutMapping("/{reviewId}/reply")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reply(
            Principal principal,
            @PathVariable UUID reviewId,
            @Valid @RequestBody WriteReviewReplyRequest request) {
        reply.execute(request.toCommand(UUID.fromString(principal.getName()), reviewId));
    }
}
