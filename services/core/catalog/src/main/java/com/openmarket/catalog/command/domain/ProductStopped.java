package com.openmarket.catalog.command.domain;

import java.util.UUID;

/**
 * 판매 중지(docs/architecture/context-map.md "catalog.product"). search가 검색에서 뺀다.
 *
 * @param reason {@link StopReason} 이름
 */
public record ProductStopped(UUID productId, long revision, String reason) {

    public static final String TYPE = "ProductStopped";
}
