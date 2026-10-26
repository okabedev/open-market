package com.openmarket.member.query.dto;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 판매자 정보. 판매자 본인(판매자 앱)과 관리자(정산 지급 화면)가 본다. 계좌 번호는 마스킹한 값만 있다. */
public record SellerView(
        UUID sellerId,
        String storeName,
        String businessNumber,
        String representativeName,
        String bankName,
        String accountHolder,
        String accountNumberMasked,
        SellerStatus status,
        @Nullable String suspendReason,
        Instant approvedAt) {

    /** QueryDSL 프로젝션용. 상태는 문자열로 받는다. */
    public SellerView(
            UUID sellerId,
            String storeName,
            String businessNumber,
            String representativeName,
            String bankName,
            String accountHolder,
            String accountNumberMasked,
            String status,
            @Nullable String suspendReason,
            Instant approvedAt) {
        this(
                sellerId,
                storeName,
                businessNumber,
                representativeName,
                bankName,
                accountHolder,
                accountNumberMasked,
                SellerStatus.valueOf(status),
                suspendReason,
                approvedAt);
    }

    /** 개인정보를 남기지 않는다(NFR-7). */
    @Override
    public String toString() {
        return "SellerView[sellerId=" + sellerId + "]";
    }
}
