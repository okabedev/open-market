package com.openmarket.auth.ui.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 가입 요청. 비밀번호 규칙(10~64자, 영문·숫자 포함)은 도메인이 검사한다.
 *
 * @param name 이름. member 프로필로 넘어간다
 */
public record RegisterAccountRequest(
        @NotBlank @Size(max = 320) String email,
        @NotBlank @Size(max = 64) String password,
        @NotBlank @Size(max = 50) String name) {

    @Override
    public String toString() {
        return "RegisterAccountRequest[***]";
    }
}
