package com.openmarket.review.command.application;

import java.util.List;
import java.util.UUID;

/**
 * 리뷰를 쓴다(REV-1).
 *
 * @param buyerId 토큰의 회원. 작성 자격의 구매자여야 한다
 * @param fileIds 리뷰 사진(file 서비스, {@code REVIEW_IMAGE}). 없으면 빈 목록
 */
public record WriteReviewCommand(
        UUID buyerId, UUID orderLineId, int rating, String content, List<UUID> fileIds) {}
