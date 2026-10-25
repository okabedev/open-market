package com.openmarket.review.query.dto;

import java.time.Instant;

/**
 * 판매자 답글.
 *
 * @param updatedAt 마지막으로 고친 시각. 고친 적이 없으면 {@code createdAt}과 같다
 */
public record ReviewReplyView(String content, Instant createdAt, Instant updatedAt) {}
