package com.openmarket.catalog.query.dto;

/** 조회용 상품 상태. 상수는 커맨드 쪽 {@code ProductStatus}와 같다. */
public enum ProductStatus {
    DRAFT,
    ON_SALE,
    STOPPED,
    BANNED
}
