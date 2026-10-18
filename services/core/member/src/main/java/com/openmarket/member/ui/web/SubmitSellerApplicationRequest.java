package com.openmarket.member.ui.web;

import jakarta.validation.constraints.NotNull;

/**
 * 입점 신청(SEL-1).
 *
 * @param storeName 상호. 2~30자
 * @param businessNumber 사업자 등록 번호. {@code 123-45-67890} 또는 숫자 10자리
 * @param accountNumber 정산 계좌 번호. 숫자 10~16자리, 하이픈은 지운다. 암호화해 저장한다
 */
public record SubmitSellerApplicationRequest(
        @NotNull String storeName,
        @NotNull String businessNumber,
        @NotNull String representativeName,
        @NotNull String bankName,
        @NotNull String accountHolder,
        @NotNull String accountNumber) {

    /** 개인정보를 남기지 않는다(NFR-7). */
    @Override
    public String toString() {
        return "SubmitSellerApplicationRequest[***]";
    }
}
