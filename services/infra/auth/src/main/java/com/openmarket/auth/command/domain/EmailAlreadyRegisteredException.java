package com.openmarket.auth.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;

/** 같은 이메일 계정이 이미 있다. 메시지에 이메일을 넣지 않는다. */
public class EmailAlreadyRegisteredException extends BusinessException {

    public EmailAlreadyRegisteredException() {
        super(ErrorType.CONFLICT, "AUTH_EMAIL_TAKEN", "이미 가입한 이메일입니다.");
    }
}
