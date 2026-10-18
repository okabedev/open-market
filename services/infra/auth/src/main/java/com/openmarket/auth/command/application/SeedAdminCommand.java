package com.openmarket.auth.command.application;

/** 관리자 시드 계정. 값은 config-repo auth.yml이 .env에서 읽는다. */
public record SeedAdminCommand(String email, String password) {

    @Override
    public String toString() {
        return "SeedAdminCommand[***]";
    }
}
