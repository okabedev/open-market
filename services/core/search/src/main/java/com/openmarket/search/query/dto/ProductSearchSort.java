package com.openmarket.search.query.dto;

/** 검색 정렬. 어떤 정렬이든 품절 상품은 뒤로 간다(SRC-3). */
public enum ProductSearchSort {
    /** 관련도. 검색어가 없으면 {@link #LATEST}와 같다. */
    RELEVANCE,
    /** 판매 시작 최신 순. */
    LATEST,
    /** 최저가 낮은 순. */
    PRICE_ASC,
    /** 최저가 높은 순. */
    PRICE_DESC,
    /** 평균 별점 높은 순, 같으면 리뷰 수 많은 순. 리뷰가 없는 상품은 뒤로 간다. */
    RATING,
    /** 판매량(구매확정 수량) 많은 순. */
    SALES
}
