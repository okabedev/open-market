package com.openmarket.member.command.application;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record ChangeAddressCommand(
        UUID memberId,
        UUID addressId,
        @Nullable String label,
        String recipientName,
        String recipientPhone,
        String zipCode,
        String baseAddress,
        @Nullable String detailAddress) {

    /** 개인정보를 남기지 않는다(NFR-7). */
    @Override
    public String toString() {
        return "ChangeAddressCommand[memberId=" + memberId + ", addressId=" + addressId + "]";
    }
}
