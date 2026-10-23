package com.openmarket.review.ui.web;

import com.openmarket.common.web.idempotency.Idempotent;
import com.openmarket.review.command.application.WriteReviewUseCase;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 구매자 리뷰 작성(REV-1). 주체는 토큰의 {@code sub}(= 회원 ID)다. */
@RestController
@RequestMapping("/v1/reviews")
public class ReviewCommandController {

    private final WriteReviewUseCase write;

    public ReviewCommandController(WriteReviewUseCase write) {
        this.write = write;
    }

    /**
     * 구매확정한 주문 항목에 리뷰를 쓴다. {@code Idempotency-Key}가 필요하다. 내 작성 자격이 없으면 404 {@code
     * REVIEW_NOT_ELIGIBLE}, 이미 썼으면 409 {@code REVIEW_ALREADY_WRITTEN}, 입력 규칙 위반은 400 {@code
     * REVIEW_INVALID_INPUT}.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Idempotent
    public WriteReviewResponse write(
            Principal principal, @Valid @RequestBody WriteReviewRequest request) {
        return new WriteReviewResponse(
                write.execute(request.toCommand(UUID.fromString(principal.getName()))));
    }
}
