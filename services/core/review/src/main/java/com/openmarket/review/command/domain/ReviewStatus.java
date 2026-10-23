package com.openmarket.review.command.domain;

/** 리뷰 상태. 숨긴 리뷰는 상품 리뷰 목록과 별점 요약에서 빠진다(docs/architecture/review.md "리뷰"). */
public enum ReviewStatus {
    VISIBLE,
    HIDDEN
}
