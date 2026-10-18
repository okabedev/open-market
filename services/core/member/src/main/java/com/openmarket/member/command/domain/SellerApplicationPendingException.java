package com.openmarket.member.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;

/** 심사 중인 입점 신청이 이미 있다. */
public class SellerApplicationPendingException extends BusinessException {

    public SellerApplicationPendingException() {
        super(ErrorType.CONFLICT, "MEMBER_APPLICATION_PENDING", "심사 중인 입점 신청이 있습니다.");
    }
}
