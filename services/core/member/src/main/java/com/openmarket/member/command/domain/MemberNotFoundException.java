package com.openmarket.member.command.domain;

import com.openmarket.common.core.error.NotFoundException;

/** 회원이 없다. 가입 직후에는 auth의 가입 이벤트가 아직 오지 않았을 수 있다. */
public class MemberNotFoundException extends NotFoundException {

    public MemberNotFoundException() {
        super("MEMBER_NOT_FOUND", "회원 정보를 찾을 수 없습니다.");
    }
}
