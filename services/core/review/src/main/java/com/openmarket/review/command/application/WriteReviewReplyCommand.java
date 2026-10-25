package com.openmarket.review.command.application;

import java.util.UUID;

/**
 * 판매자 답글. 처음이면 달고, 있으면 고친다.
 *
 * @param sellerId 토큰의 {@code sub}. 리뷰 상품의 판매자여야 한다
 * @param content 1~1,000자. 앞뒤 공백은 지운다
 */
public record WriteReviewReplyCommand(UUID sellerId, UUID reviewId, String content) {}
