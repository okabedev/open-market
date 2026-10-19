package com.openmarket.catalog.command.domain;

import java.util.UUID;

/**
 * 관리자 제재(CAT-5). search가 검색에서 빼고, notification이 판매자에게 알린다(M4).
 *
 * @param reason 관리자가 쓴 사유. 판매자에게 보인다
 */
public record ProductBanned(UUID productId, long revision, UUID sellerId, String reason) {

    public static final String TYPE = "ProductBanned";
}
