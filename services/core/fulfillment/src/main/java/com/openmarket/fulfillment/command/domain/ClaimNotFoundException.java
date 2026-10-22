package com.openmarket.fulfillment.command.domain;

import com.openmarket.common.core.error.NotFoundException;

/** 판매자가 처리할 클레임이 없다(없는 클레임, 다른 판매자의 클레임, 발송 전 취소 클레임). 404다. */
public class ClaimNotFoundException extends NotFoundException {

    public ClaimNotFoundException() {
        super("FULFILLMENT_CLAIM_NOT_FOUND", "클레임을 찾을 수 없습니다.");
    }
}
