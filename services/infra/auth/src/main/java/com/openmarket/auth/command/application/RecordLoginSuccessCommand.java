package com.openmarket.auth.command.application;

import java.util.UUID;

/** 로그인에 성공한 계정. */
public record RecordLoginSuccessCommand(UUID accountId) {}
