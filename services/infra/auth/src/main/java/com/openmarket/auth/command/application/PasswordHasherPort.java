package com.openmarket.auth.command.application;

/** 비밀번호 해시. 구현은 Spring Security {@code PasswordEncoder}다(docs/architecture/auth.md "보안 기본값"). */
@FunctionalInterface
public interface PasswordHasherPort {

    String hash(String rawPassword);
}
