package com.openmarket.member.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;

/** 회원 정보나 배송지 입력이 규칙에 맞지 않는다. 메시지에 입력값을 넣지 않는다. */
public class InvalidMemberInputException extends BusinessException {

    public InvalidMemberInputException(String message) {
        super(ErrorType.INVALID, "MEMBER_INVALID_INPUT", message);
    }
}
