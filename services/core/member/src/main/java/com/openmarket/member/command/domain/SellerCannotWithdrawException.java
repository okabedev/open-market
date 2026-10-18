package com.openmarket.member.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;

/** 판매자는 탈퇴할 수 없다. 판매자 정리는 정산(M5)과 함께 만든다. */
public class SellerCannotWithdrawException extends BusinessException {

    public SellerCannotWithdrawException() {
        super(
                ErrorType.UNPROCESSABLE,
                "MEMBER_SELLER_CANNOT_WITHDRAW",
                "판매자는 탈퇴할 수 없습니다. 고객센터로 문의해 주세요.");
    }
}
