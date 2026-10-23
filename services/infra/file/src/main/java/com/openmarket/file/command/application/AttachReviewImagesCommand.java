package com.openmarket.file.command.application;

import java.util.List;
import java.util.UUID;

/**
 * 리뷰의 사진을 리뷰에 붙인다({@code ReviewCreated}).
 *
 * @param buyerId 리뷰 작성자. 이 사람이 올린 파일만 붙는다
 * @param sellerId 리뷰 상품의 판매자
 * @param fileIds 리뷰 사진. 없으면 빈 목록
 */
public record AttachReviewImagesCommand(
        UUID reviewId, UUID buyerId, UUID sellerId, List<UUID> fileIds) {}
