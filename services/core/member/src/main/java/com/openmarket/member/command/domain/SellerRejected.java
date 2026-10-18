package com.openmarket.member.command.domain;

import java.util.UUID;

/** 입점 반려(SEL-2)(docs/architecture/context-map.md "member.seller"). */
public record SellerRejected(UUID memberId, UUID applicationId, String reason) {

    public static final String TYPE = "SellerRejected";
}
