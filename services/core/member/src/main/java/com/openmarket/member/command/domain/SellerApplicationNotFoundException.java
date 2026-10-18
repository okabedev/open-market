package com.openmarket.member.command.domain;

import com.openmarket.common.core.error.NotFoundException;

public class SellerApplicationNotFoundException extends NotFoundException {

    public SellerApplicationNotFoundException() {
        super("MEMBER_APPLICATION_NOT_FOUND", "입점 신청을 찾을 수 없습니다.");
    }
}
