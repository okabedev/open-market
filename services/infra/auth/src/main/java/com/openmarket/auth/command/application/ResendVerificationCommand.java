package com.openmarket.auth.command.application;

/** 인증 메일을 다시 보낸다. 로그인 전에도 쓸 수 있도록 이메일로 받는다. */
public record ResendVerificationCommand(String email) {

    @Override
    public String toString() {
        return "ResendVerificationCommand[***]";
    }
}
