package com.openmarket.file.command.application;

import java.util.List;
import java.util.UUID;

/**
 * 상품 스냅샷의 이미지 목록으로 첨부를 맞춘다.
 *
 * @param revision 상품 리비전. 반영한 것보다 작거나 같으면 무시한다
 * @param sellerId 상품의 판매자. 이 사람이 올린 파일만 붙는다
 * @param imageFileIds 상품에 붙어 있어야 할 파일 전체
 */
public record SyncProductImagesCommand(
        UUID productId, long revision, UUID sellerId, List<UUID> imageFileIds) {}
