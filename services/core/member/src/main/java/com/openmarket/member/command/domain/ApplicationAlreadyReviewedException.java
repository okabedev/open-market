package com.openmarket.member.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;

/** 이미 다른 결과로 심사한 신청이다. 같은 결과로 다시 심사하면 오류가 아니다. */
public class ApplicationAlreadyReviewedException extends BusinessException {

    public ApplicationAlreadyReviewedException() {
        super(ErrorType.CONFLICT, "MEMBER_APPLICATION_ALREADY_REVIEWED", "이미 다른 결과로 심사한 신청입니다.");
    }
}
