package com.openmarket.auth.command.application;

/** 비밀번호를 확인한 로그인 세션을 2단계 인증 결과에 따라 마치거나 끝낸다. 구현은 Spring Security 세션이다(infra). 2단계 화면이 쓴다. */
public interface SecondFactorSessionPort {

    /** 2단계 인증을 마친 세션으로 바꾸고(세션 ID를 새로 만든다) 돌아갈 주소를 준다. 저장한 인가 요청이 있으면 그 주소, 없으면 {@code /}다. */
    String complete();

    /** 로그인 세션을 끝낸다. 계정이 잠겼을 때다. */
    void end();
}
