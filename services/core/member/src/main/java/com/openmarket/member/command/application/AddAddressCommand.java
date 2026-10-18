package com.openmarket.member.command.application;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 배송지를 더한다.
 *
 * @param asDefault 기본 배송지로 지정한다. 첫 배송지는 지정하지 않아도 기본이 된다
 */
public record AddAddressCommand(
        UUID memberId,
        @Nullable String label,
        String recipientName,
        String recipientPhone,
        String zipCode,
        String baseAddress,
        @Nullable String detailAddress,
        boolean asDefault) {

    /** 개인정보를 남기지 않는다(NFR-7). */
    @Override
    public String toString() {
        return "AddAddressCommand[memberId=" + memberId + "]";
    }
}
