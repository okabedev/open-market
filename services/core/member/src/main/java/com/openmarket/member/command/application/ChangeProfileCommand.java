package com.openmarket.member.command.application;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 이름과 연락처를 바꾼다.
 *
 * @param phone 비우면 연락처를 지운다
 */
public record ChangeProfileCommand(UUID memberId, String name, @Nullable String phone) {

    /** 개인정보를 남기지 않는다(NFR-7). */
    @Override
    public String toString() {
        return "ChangeProfileCommand[memberId=" + memberId + "]";
    }
}
