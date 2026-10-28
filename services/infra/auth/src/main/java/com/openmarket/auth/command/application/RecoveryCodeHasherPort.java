package com.openmarket.auth.command.application;

/** 복구 코드 해시. 원문은 저장하지 않는다. 구현은 Spring Security {@code PasswordEncoder}다. */
public interface RecoveryCodeHasherPort {

    String hash(String code);

    boolean matches(String code, String hash);
}
