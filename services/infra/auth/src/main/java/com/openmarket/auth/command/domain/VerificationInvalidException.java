package com.openmarket.auth.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;

/** 인증 토큰이 없다(잘못 복사했거나 지어낸 링크). */
public class VerificationInvalidException extends BusinessException {

    public VerificationInvalidException() {
        super(ErrorType.UNPROCESSABLE, "AUTH_VERIFICATION_INVALID", "인증 링크가 올바르지 않습니다.");
    }
}
