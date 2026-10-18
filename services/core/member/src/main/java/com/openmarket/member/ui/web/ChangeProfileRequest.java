package com.openmarket.member.ui.web;

import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

/**
 * 프로필 변경.
 *
 * @param name 50자까지
 * @param phone 하이픈으로 나눈 국내 전화번호(예: 010-1234-5678). 비우면 지운다
 */
public record ChangeProfileRequest(@NotNull String name, @Nullable String phone) {

    /** 개인정보를 남기지 않는다(NFR-7). */
    @Override
    public String toString() {
        return "ChangeProfileRequest[***]";
    }
}
