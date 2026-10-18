package com.openmarket.member.query.dto;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 본인 입점 신청. 본인에게는 원문을 주고, 계좌 번호만 마스킹한 값을 준다(원문은 저장하지 않는다). */
public record MySellerApplicationView(
        UUID applicationId,
        String storeName,
        String businessNumber,
        String representativeName,
        String bankName,
        String accountHolder,
        String accountNumberMasked,
        SellerApplicationStatus status,
        @Nullable String rejectReason,
        Instant submittedAt,
        @Nullable Instant reviewedAt) {

    /** QueryDSL 프로젝션용. 상태는 문자열로 받는다. */
    public MySellerApplicationView(
            UUID applicationId,
            String storeName,
            String businessNumber,
            String representativeName,
            String bankName,
            String accountHolder,
            String accountNumberMasked,
            String status,
            @Nullable String rejectReason,
            Instant submittedAt,
            @Nullable Instant reviewedAt) {
        this(
                applicationId,
                storeName,
                businessNumber,
                representativeName,
                bankName,
                accountHolder,
                accountNumberMasked,
                SellerApplicationStatus.valueOf(status),
                rejectReason,
                submittedAt,
                reviewedAt);
    }

    /** 개인정보를 남기지 않는다(NFR-7). */
    @Override
    public String toString() {
        return "MySellerApplicationView[applicationId=" + applicationId + "]";
    }
}
