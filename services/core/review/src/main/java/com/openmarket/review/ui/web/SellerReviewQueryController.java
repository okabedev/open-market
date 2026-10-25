package com.openmarket.review.ui.web;

import com.openmarket.review.query.application.ReviewQueryService;
import com.openmarket.review.query.dto.CursorPageView;
import com.openmarket.review.query.dto.ReviewView;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.security.Principal;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 판매자 리뷰 목록. 주체는 토큰의 {@code sub}(= 판매자 ID)다. */
@RestController
@RequestMapping("/v1/seller/reviews")
public class SellerReviewQueryController {

    private final ReviewQueryService reviews;

    public SellerReviewQueryController(ReviewQueryService reviews) {
        this.reviews = reviews;
    }

    /** 자기 상품의 리뷰. 숨긴 리뷰도 상태와 함께 보인다. 최근 순 커서 목록이고, 커서가 틀리면 400 {@code REVIEW_INVALID_CURSOR}. */
    @GetMapping
    public CursorPageView<ReviewView> list(
            Principal principal,
            @RequestParam(required = false) @Nullable String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return reviews.sellerReviews(UUID.fromString(principal.getName()), cursor, size);
    }
}
