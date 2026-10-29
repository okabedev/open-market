package com.openmarket.auth.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;

/** 비밀번호가 규칙(ACC-1)에 맞지 않는다. */
public class InvalidPasswordException extends BusinessException {

    public InvalidPasswordException() {
        super(
                ErrorType.INVALID,
                "AUTH_INVALID_PASSWORD",
                "비밀번호는 10자 이상 64자 이하(한글은 22자 안팎)이고 영문과 숫자를 모두 포함해야 합니다.");
    }
}
