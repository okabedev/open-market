package com.openmarket.member.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;

/** 이메일 인증 전에는 입점을 신청할 수 없다. */
public class EmailNotVerifiedException extends BusinessException {

    public EmailNotVerifiedException() {
        super(ErrorType.UNPROCESSABLE, "MEMBER_EMAIL_NOT_VERIFIED", "이메일 인증을 마친 뒤 신청할 수 있습니다.");
    }
}
