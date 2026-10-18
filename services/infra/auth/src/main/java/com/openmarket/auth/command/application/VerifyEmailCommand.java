package com.openmarket.auth.command.application;

/** 인증 링크의 토큰 원문으로 이메일을 인증한다. */
public record VerifyEmailCommand(String token) {

    @Override
    public String toString() {
        return "VerifyEmailCommand[***]";
    }
}
