package com.openmarket.member.command.application;

import java.util.UUID;

/**
 * auth의 {@code AccountRegistered}로 회원을 만든다.
 *
 * @param memberId accountId와 같은 값
 */
public record RegisterMemberCommand(UUID memberId, String email, String name) {

    /** 개인정보를 남기지 않는다(NFR-7). */
    @Override
    public String toString() {
        return "RegisterMemberCommand[memberId=" + memberId + "]";
    }
}
