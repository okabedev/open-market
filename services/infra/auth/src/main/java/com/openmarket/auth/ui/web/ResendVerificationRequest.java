package com.openmarket.auth.ui.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 인증 메일을 다시 받을 이메일. */
public record ResendVerificationRequest(@NotBlank @Size(max = 320) String email) {

    @Override
    public String toString() {
        return "ResendVerificationRequest[***]";
    }
}
