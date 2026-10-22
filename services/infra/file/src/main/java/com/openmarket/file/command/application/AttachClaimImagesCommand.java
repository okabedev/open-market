package com.openmarket.file.command.application;

import java.util.List;
import java.util.UUID;

/**
 * 클레임 신청의 사진을 클레임에 붙인다({@code ClaimRequested}).
 *
 * @param buyerId 클레임의 구매자. 이 사람이 올린 파일만 붙는다
 * @param sellerId 클레임의 판매자. 사진을 볼 수 있다
 * @param fileIds 클레임 사진. 없으면 빈 목록
 */
public record AttachClaimImagesCommand(
        UUID claimId, UUID buyerId, UUID sellerId, List<UUID> fileIds) {}
