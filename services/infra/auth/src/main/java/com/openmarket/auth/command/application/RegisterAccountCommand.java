package com.openmarket.auth.command.application;

/**
 * 이메일 가입(ACC-1).
 *
 * @param name 이름. auth는 저장하지 않고 {@code AccountRegistered}로 member에 넘긴다
 */
public record RegisterAccountCommand(String email, String password, String name) {

    @Override
    public String toString() {
        return "RegisterAccountCommand[***]";
    }
}
