package com.openmarket.auth.command.application;

/** 로그인 폼에 입력한 이메일로 실패를 기록한다. */
public record RecordLoginFailureCommand(String email) {

    @Override
    public String toString() {
        return "RecordLoginFailureCommand[***]";
    }
}
