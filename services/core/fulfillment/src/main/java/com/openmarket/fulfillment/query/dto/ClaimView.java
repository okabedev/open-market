package com.openmarket.fulfillment.query.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 클레임 하나(docs/architecture/fulfillment.md "클레임").
 *
 * @param description 구매자 설명. 발송 전 취소는 비어 있다
 * @param fileIds 클레임 사진(file의 비공개 경로로 받는다). 신청 순서
 * @param restock 반품의 재입고 여부. 반품 승인 뒤에만
 * @param refundAmount 환불 금액(원). 교환은 0, 반품은 승인 때 정한다
 * @param refundFailureCode 마지막 환불 실패 코드. 환불이 실패한 채 승인 상태일 때만
 * @param approvedAt 승인 시각. 승인됐을 때만
 * @param rejectionReason 거부 사유. 거부됐을 때만
 * @param rejectedAt 거부 시각. 거부됐을 때만
 * @param completedAt 완료 시각. 끝났을 때만
 */
public record ClaimView(
        UUID claimId,
        ClaimType type,
        ClaimStatus status,
        ClaimReason reason,
        Fault fault,
        @Nullable String description,
        List<UUID> fileIds,
        @Nullable Boolean restock,
        long refundAmount,
        @Nullable String refundFailureCode,
        @Nullable String rejectionReason,
        Instant requestedAt,
        @Nullable Instant approvedAt,
        @Nullable Instant rejectedAt,
        @Nullable Instant completedAt) {}
