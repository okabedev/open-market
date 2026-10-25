package com.openmarket.review.query.dto;

/** 리뷰 상태(docs/architecture/review.md "리뷰"). 숨긴 리뷰는 공개 목록과 별점 요약에서 빠진다. */
public enum ReviewStatus {
    VISIBLE,
    HIDDEN
}
