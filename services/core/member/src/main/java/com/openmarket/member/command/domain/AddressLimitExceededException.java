package com.openmarket.member.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;

/** 배송지가 이미 최대 개수다(ACC-3). */
public class AddressLimitExceededException extends BusinessException {

    public AddressLimitExceededException() {
        super(
                ErrorType.UNPROCESSABLE,
                "MEMBER_ADDRESS_LIMIT",
                "배송지는 " + Member.MAX_ADDRESSES + "개까지 등록할 수 있습니다.");
    }
}
