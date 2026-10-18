package com.openmarket.auth.ui.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 인증 링크의 {@code token} 값. */
public record VerifyEmailRequest(@NotBlank @Size(max = 128) String token) {

    @Override
    public String toString() {
        return "VerifyEmailRequest[***]";
    }
}
