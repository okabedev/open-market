package com.openmarket.auth.command.domain;

/** 계정 역할. 토큰의 {@code roles} 클레임에 그대로 나간다(docs/architecture/auth.md "토큰"). */
public enum Role {
    BUYER,
    SELLER,
    ADMIN
}
