package com.openmarket.review.query.application;

import com.openmarket.review.query.dao.RatingSummaryQueryDao;
import com.openmarket.review.query.dto.RatingSummaryView;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 상품 별점 요약 조회(공개). 상품 상세는 BFF가 catalog 상세와 함께 부른다. */
@Service
@Transactional(readOnly = true)
public class RatingSummaryQueryService {

    private final RatingSummaryQueryDao summaries;

    public RatingSummaryQueryService(RatingSummaryQueryDao summaries) {
        this.summaries = summaries;
    }

    /** 상품의 별점 요약. 리뷰가 없거나 없는 상품이면 리뷰 수 0, 평균 없음이다(상품이 있는지는 보지 않는다). */
    public RatingSummaryView summary(UUID productId) {
        return summaries.find(productId).orElseGet(() -> RatingSummaryView.empty(productId));
    }
}
