package com.openmarket.member.ui.web;

import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

/** 배송지 전체를 바꾼다. 규칙은 {@link AddAddressRequest}와 같다. 기본 배송지 지정은 따로 한다. */
public record ChangeAddressRequest(
        @Nullable String label,
        @NotNull String recipientName,
        @NotNull String recipientPhone,
        @NotNull String zipCode,
        @NotNull String baseAddress,
        @Nullable String detailAddress) {

    /** 개인정보를 남기지 않는다(NFR-7). */
    @Override
    public String toString() {
        return "ChangeAddressRequest[***]";
    }
}
