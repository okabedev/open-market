package com.openmarket.catalog.command.domain;

import java.util.UUID;

/** 판매 재개(판매자, 판매자 정지 해제). search가 검색에 다시 넣는다. */
public record ProductResumed(UUID productId, long revision) {

    public static final String TYPE = "ProductResumed";
}
