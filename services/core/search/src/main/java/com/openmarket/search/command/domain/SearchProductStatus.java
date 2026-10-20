package com.openmarket.search.command.domain;

/** 검색 상품이 아는 상품 상태. catalog 상품 상태와 이름이 같다. 판매 중({@link #ON_SALE})만 검색에 나온다. */
public enum SearchProductStatus {
    DRAFT,
    ON_SALE,
    STOPPED,
    BANNED
}
