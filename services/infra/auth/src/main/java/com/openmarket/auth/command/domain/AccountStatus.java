package com.openmarket.auth.command.domain;

/** 계정 상태. {@link #DEACTIVATED}는 로그인할 수 없다(탈퇴 등). */
public enum AccountStatus {
    ACTIVE,
    DEACTIVATED
}
