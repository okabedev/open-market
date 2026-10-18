package com.openmarket.auth.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;

/** 이메일 형식이 맞지 않는다. 메시지에 입력값(개인정보)을 넣지 않는다. */
public class InvalidEmailException extends BusinessException {

    public InvalidEmailException() {
        super(ErrorType.INVALID, "AUTH_INVALID_EMAIL", "이메일 형식이 올바르지 않습니다.");
    }
}
