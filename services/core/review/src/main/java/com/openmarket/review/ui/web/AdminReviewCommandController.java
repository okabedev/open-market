package com.openmarket.review.ui.web;

import com.openmarket.review.command.application.ChangeReviewVisibilityUseCase;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 리뷰 숨김·보임(REV-3, ADM-2). 주체는 토큰의 {@code sub}(= 관리자 ID)이고 사유와 함께 감사 로그에 남는다. 같은 상태로 다시 하면 204이고
 * 아무것도 바뀌지 않는다. 없으면 404 {@code REVIEW_NOT_FOUND}, 사유 규칙 위반은 400 {@code REVIEW_INVALID_INPUT}.
 */
@RestController
@RequestMapping("/v1/admin/reviews")
public class AdminReviewCommandController {

    private final ChangeReviewVisibilityUseCase visibility;

    public AdminReviewCommandController(ChangeReviewVisibilityUseCase visibility) {
        this.visibility = visibility;
    }

    /** 리뷰를 숨긴다. 상품 리뷰 목록과 별점 요약에서 빠진다. */
    @PostMapping("/{reviewId}/hide")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void hide(
            Principal principal,
            @PathVariable UUID reviewId,
            @Valid @RequestBody ChangeReviewVisibilityRequest request) {
        visibility.execute(
                request.toCommand(UUID.fromString(principal.getName()), reviewId, false));
    }

    /** 숨긴 리뷰를 다시 보인다. */
    @PostMapping("/{reviewId}/show")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void show(
            Principal principal,
            @PathVariable UUID reviewId,
            @Valid @RequestBody ChangeReviewVisibilityRequest request) {
        visibility.execute(request.toCommand(UUID.fromString(principal.getName()), reviewId, true));
    }
}
