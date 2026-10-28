package com.openmarket.auth.command.application;

import java.util.UUID;

/** 로그인할 때 입력한 인증 앱 코드(숫자 6자리)나 복구 코드. */
public record VerifySecondFactorCommand(UUID accountId, String code) {}
