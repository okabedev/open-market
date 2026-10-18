package com.openmarket.member.command.domain;

/** 입점 신청 상태(docs/glossary.md "입점 신청"). */
public enum SellerApplicationStatus {
    SUBMITTED,
    APPROVED,
    REJECTED,
    /** 심사 중에 신청자가 탈퇴했다. */
    CANCELED
}
