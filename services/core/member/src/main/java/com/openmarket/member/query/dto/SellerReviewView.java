package com.openmarket.member.query.dto;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 관리자 판매자 목록. 대표자는 마스킹해서 준다(NFR-7). */
public record SellerReviewView(
        UUID sellerId,
        String storeName,
        String businessNumber,
        String representativeName,
        SellerStatus status,
        @Nullable String suspendReason,
        Instant approvedAt) {

    /** QueryDSL 프로젝션용. 상태는 문자열로 받는다. 마스킹은 조회 서비스가 한다. */
    public SellerReviewView(
            UUID sellerId,
            String storeName,
            String businessNumber,
            String representativeName,
            String status,
            @Nullable String suspendReason,
            Instant approvedAt) {
        this(
                sellerId,
                storeName,
                businessNumber,
                representativeName,
                SellerStatus.valueOf(status),
                suspendReason,
                approvedAt);
    }

    /** 개인정보를 남기지 않는다(NFR-7). */
    @Override
    public String toString() {
        return "SellerReviewView[sellerId=" + sellerId + "]";
    }
}
