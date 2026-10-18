package com.openmarket.member.command.domain;

import java.util.UUID;

/**
 * 판매자 정지(SEL-3). auth가 SELLER 역할을 떼고 catalog가 상품을 판매 중지한다(docs/architecture/context-map.md
 * "member.seller").
 */
public record SellerSuspended(UUID sellerId, String reason) {

    public static final String TYPE = "SellerSuspended";
}
