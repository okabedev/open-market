package com.openmarket.member.ui.web;

import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

/**
 * 배송지 추가.
 *
 * @param label 이름표(예: 집). 20자까지, 비워도 된다
 * @param recipientPhone 하이픈으로 나눈 국내 전화번호
 * @param zipCode 숫자 5자리
 * @param asDefault 기본 배송지로 지정한다. 없으면 {@code false}
 */
public record AddAddressRequest(
        @Nullable String label,
        @NotNull String recipientName,
        @NotNull String recipientPhone,
        @NotNull String zipCode,
        @NotNull String baseAddress,
        @Nullable String detailAddress,
        @Nullable Boolean asDefault) {

    /** 개인정보를 남기지 않는다(NFR-7). */
    @Override
    public String toString() {
        return "AddAddressRequest[***]";
    }
}
