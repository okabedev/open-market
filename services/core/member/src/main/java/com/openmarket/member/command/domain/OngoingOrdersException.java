package com.openmarket.member.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;

/** 진행 중인 주문이나 클레임이 있어 탈퇴할 수 없다(ACC-4). */
public class OngoingOrdersException extends BusinessException {

    public OngoingOrdersException() {
        super(ErrorType.UNPROCESSABLE, "MEMBER_ONGOING_ORDERS", "진행 중인 주문이나 클레임이 있어 탈퇴할 수 없습니다.");
    }
}
