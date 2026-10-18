package com.openmarket.auth.command.application;

/** 인증 토큰으로 메일에 넣을 링크를 만든다. 링크는 shop 앱의 인증 화면이다(config-repo auth.yml). */
@FunctionalInterface
public interface VerificationLinkPort {

    String link(String rawToken);
}
