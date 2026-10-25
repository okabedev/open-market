package com.openmarket.review.ui.web;

import com.openmarket.review.query.application.ReviewQueryService;
import com.openmarket.review.query.dto.CursorPageView;
import com.openmarket.review.query.dto.ProductReviewView;
import com.openmarket.review.query.dto.ReviewView;
import com.openmarket.review.query.dto.WritableReviewView;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.security.Principal;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공개 상품 리뷰와 구매자 리뷰 목록. 목록은 최근 순 커서 목록이고, 커서가 틀리면 400 {@code REVIEW_INVALID_CURSOR}. 구매자는 토큰의 {@code
 * sub}(= 회원 ID)다.
 */
@RestController
public class ReviewQueryController {

    private final ReviewQueryService reviews;

    public ReviewQueryController(ReviewQueryService reviews) {
        this.reviews = reviews;
    }

    /** 상품의 보이는 리뷰. 토큰 없이 부른다(상품 상세). 숨긴 리뷰는 빠진다. */
    @GetMapping("/v1/products/{productId}/reviews")
    public CursorPageView<ProductReviewView> productReviews(
            @PathVariable UUID productId,
            @RequestParam(required = false) @Nullable String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return reviews.productReviews(productId, cursor, size);
    }

    /** 내가 쓴 리뷰. 숨긴 리뷰도 상태와 함께 보인다. */
    @GetMapping("/v1/reviews")
    public CursorPageView<ReviewView> myReviews(
            Principal principal,
            @RequestParam(required = false) @Nullable String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return reviews.buyerReviews(UUID.fromString(principal.getName()), cursor, size);
    }

    /** 리뷰를 쓸 수 있는 구매확정 주문 항목. 구매확정 최근 순. */
    @GetMapping("/v1/reviews/writable")
    public CursorPageView<WritableReviewView> writable(
            Principal principal,
            @RequestParam(required = false) @Nullable String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return reviews.writable(UUID.fromString(principal.getName()), cursor, size);
    }
}
