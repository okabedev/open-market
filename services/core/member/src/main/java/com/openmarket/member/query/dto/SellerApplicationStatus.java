package com.openmarket.member.query.dto;

/** 조회용 입점 신청 상태. 상수는 도메인 enum과 같다(conventions.md "경계를 넘는 타입"). */
public enum SellerApplicationStatus {
    SUBMITTED,
    APPROVED,
    REJECTED,
    CANCELED
}
