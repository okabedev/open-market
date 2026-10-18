package com.openmarket.member.query.dto;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 본인 배송지. 본인에게는 원문을 준다(NFR-7, docs/prd.md). */
public record AddressView(
        UUID addressId,
        @Nullable String label,
        String recipientName,
        String recipientPhone,
        String zipCode,
        String baseAddress,
        @Nullable String detailAddress,
        boolean isDefault,
        Instant createdAt) {

    /** 개인정보를 남기지 않는다(NFR-7). */
    @Override
    public String toString() {
        return "AddressView[addressId=" + addressId + "]";
    }
}
