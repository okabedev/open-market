package com.openmarket.auth.command.application;

import java.util.UUID;

/** 등록 화면에서 입력한 인증 앱 코드. */
public record ConfirmTotpEnrollmentCommand(UUID accountId, String code) {}
