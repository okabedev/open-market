package com.openmarket.review.ui.web;

import com.openmarket.review.query.application.ReviewQueryService;
import com.openmarket.review.query.dto.PageView;
import com.openmarket.review.query.dto.ReviewStatus;
import com.openmarket.review.query.dto.ReviewView;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 리뷰 목록. 쪽 번호 목록이다. */
@RestController
@RequestMapping("/v1/admin/reviews")
public class AdminReviewQueryController {

    private final ReviewQueryService reviews;

    public AdminReviewQueryController(ReviewQueryService reviews) {
        this.reviews = reviews;
    }

    /** 모든 리뷰. 상태, 상품, 판매자로 거른다. 주지 않으면 거르지 않는다. 최근 순. */
    @GetMapping
    public PageView<ReviewView> list(
            @RequestParam(required = false) @Nullable ReviewStatus status,
            @RequestParam(required = false) @Nullable UUID productId,
            @RequestParam(required = false) @Nullable UUID sellerId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return reviews.adminReviews(status, productId, sellerId, page, size);
    }
}
