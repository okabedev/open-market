package com.openmarket.review.ui.web;

import com.openmarket.review.query.application.RatingSummaryQueryService;
import com.openmarket.review.query.dto.RatingSummaryView;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 공개 별점 요약 조회. 토큰 없이 부른다(상품 상세). */
@RestController
@RequestMapping("/v1/products")
public class RatingSummaryQueryController {

    private final RatingSummaryQueryService summaries;

    public RatingSummaryQueryController(RatingSummaryQueryService summaries) {
        this.summaries = summaries;
    }

    /** 상품의 별점 요약. 리뷰가 없으면 리뷰 수 0, 평균 없음이다. */
    @GetMapping("/{productId}/rating-summary")
    public RatingSummaryView ratingSummary(@PathVariable UUID productId) {
        return summaries.summary(productId);
    }
}
