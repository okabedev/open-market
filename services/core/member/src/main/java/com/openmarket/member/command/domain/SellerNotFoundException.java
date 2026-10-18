package com.openmarket.member.command.domain;

import com.openmarket.common.core.error.NotFoundException;

public class SellerNotFoundException extends NotFoundException {

    public SellerNotFoundException() {
        super("MEMBER_SELLER_NOT_FOUND", "판매자를 찾을 수 없습니다.");
    }
}
