package com.openmarket.review.query.application;

import com.openmarket.review.query.dao.ReviewQueryDao;
import com.openmarket.review.query.dto.CursorPageView;
import com.openmarket.review.query.dto.PageView;
import com.openmarket.review.query.dto.ProductReviewView;
import com.openmarket.review.query.dto.ReviewStatus;
import com.openmarket.review.query.dto.ReviewView;
import com.openmarket.review.query.dto.WritableReviewView;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 리뷰 목록 조회(공개, 구매자, 판매자, 관리자). 커서가 틀리면 400 {@code REVIEW_INVALID_CURSOR}. */
@Service
@Transactional(readOnly = true)
public class ReviewQueryService {

    private final ReviewQueryDao reviews;

    public ReviewQueryService(ReviewQueryDao reviews) {
        this.reviews = reviews;
    }

    public CursorPageView<ProductReviewView> productReviews(
            UUID productId, @Nullable String cursor, int size) {
        return reviews.findProductReviews(productId, cursor, size);
    }

    public CursorPageView<ReviewView> buyerReviews(
            UUID buyerId, @Nullable String cursor, int size) {
        return reviews.findBuyerReviews(buyerId, cursor, size);
    }

    public CursorPageView<WritableReviewView> writable(
            UUID buyerId, @Nullable String cursor, int size) {
        return reviews.findWritable(buyerId, cursor, size);
    }

    public CursorPageView<ReviewView> sellerReviews(
            UUID sellerId, @Nullable String cursor, int size) {
        return reviews.findSellerReviews(sellerId, cursor, size);
    }

    public PageView<ReviewView> adminReviews(
            @Nullable ReviewStatus status,
            @Nullable UUID productId,
            @Nullable UUID sellerId,
            int page,
            int size) {
        return reviews.findAdminPage(status, productId, sellerId, page, size);
    }
}
