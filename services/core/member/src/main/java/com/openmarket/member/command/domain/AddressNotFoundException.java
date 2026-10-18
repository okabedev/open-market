package com.openmarket.member.command.domain;

import com.openmarket.common.core.error.NotFoundException;

/** 회원에게 그 배송지가 없다. 다른 회원의 배송지도 같은 오류다. */
public class AddressNotFoundException extends NotFoundException {

    public AddressNotFoundException() {
        super("MEMBER_ADDRESS_NOT_FOUND", "배송지를 찾을 수 없습니다.");
    }
}
