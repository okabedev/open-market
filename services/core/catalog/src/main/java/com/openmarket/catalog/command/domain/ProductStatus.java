package com.openmarket.catalog.command.domain;

/**
 * 상품 상태(docs/glossary.md "상품"). {@code DRAFT} → {@code ON_SALE} ↔ {@code STOPPED}, 관리자 제재 시 {@code
 * BANNED}.
 */
public enum ProductStatus {
    DRAFT,
    ON_SALE,
    STOPPED,
    BANNED
}
