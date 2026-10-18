package com.openmarket.auth.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;

/** 인증 링크가 만료됐다. 화면은 재발송을 안내한다. */
public class VerificationExpiredException extends BusinessException {

    public VerificationExpiredException() {
        super(
                ErrorType.UNPROCESSABLE,
                "AUTH_VERIFICATION_EXPIRED",
                "인증 링크가 만료됐습니다. 인증 메일을 다시 받아 주세요.");
    }
}
