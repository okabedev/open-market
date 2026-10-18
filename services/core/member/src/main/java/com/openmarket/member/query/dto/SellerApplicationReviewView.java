package com.openmarket.member.query.dto;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 관리자 심사 화면의 입점 신청. 신청자 이메일, 대표자, 예금주는 마스킹해서 준다(NFR-7). 사업자 번호와 상호는 공개 정보라 원문이다. */
public record SellerApplicationReviewView(
        UUID applicationId,
        UUID memberId,
        String applicantEmail,
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

    /** QueryDSL 프로젝션용. 상태는 문자열로 받는다. 마스킹은 조회 서비스가 한다. */
    public SellerApplicationReviewView(
            UUID applicationId,
            UUID memberId,
            String applicantEmail,
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
                memberId,
                applicantEmail,
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
        return "SellerApplicationReviewView[applicationId=" + applicationId + "]";
    }
}
