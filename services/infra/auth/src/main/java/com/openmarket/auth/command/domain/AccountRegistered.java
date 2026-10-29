package com.openmarket.auth.command.domain;

import java.util.UUID;

/**
 * 이메일 가입 완료({@code auth.account}). member가 프로필을 만든다. 목록은 docs/architecture/context-map.md에 있다.
 *
 * @param name 가입할 때 받은 이름. auth는 저장하지 않고 member로 넘긴다
 */
public record AccountRegistered(UUID accountId, String email, String name) {

    public static final String TYPE = "AccountRegistered";

    /** 개인정보를 남기지 않는다(NFR-7). */
    @Override
    public String toString() {
        return "AccountRegistered[accountId=" + accountId + "]";
    }
}
