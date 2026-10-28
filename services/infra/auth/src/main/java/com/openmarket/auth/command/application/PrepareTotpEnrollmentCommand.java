package com.openmarket.auth.command.application;

import java.util.UUID;

/** 2단계 인증을 등록하려는 관리자. 비밀번호를 확인한 로그인 세션의 계정이다. */
public record PrepareTotpEnrollmentCommand(UUID accountId) {}
