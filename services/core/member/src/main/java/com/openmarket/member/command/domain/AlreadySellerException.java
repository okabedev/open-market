package com.openmarket.member.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;

/** 이미 판매자다. */
public class AlreadySellerException extends BusinessException {

    public AlreadySellerException() {
        super(ErrorType.CONFLICT, "MEMBER_ALREADY_SELLER", "이미 판매자입니다.");
    }
}
