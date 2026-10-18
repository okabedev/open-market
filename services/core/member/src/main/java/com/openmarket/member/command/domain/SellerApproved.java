package com.openmarket.member.command.domain;

import java.util.UUID;

/** 입점 승인(SEL-2). auth가 SELLER 역할을 붙인다(docs/architecture/context-map.md "member.seller"). */
public record SellerApproved(UUID sellerId, String storeName) {

    public static final String TYPE = "SellerApproved";
}
