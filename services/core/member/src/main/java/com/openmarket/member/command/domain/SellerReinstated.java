package com.openmarket.member.command.domain;

import java.util.UUID;

/** 정지 해제. auth가 SELLER 역할을 다시 붙인다(docs/architecture/context-map.md "member.seller"). */
public record SellerReinstated(UUID sellerId) {

    public static final String TYPE = "SellerReinstated";
}
