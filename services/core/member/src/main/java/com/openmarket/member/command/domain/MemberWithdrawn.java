package com.openmarket.member.command.domain;

import java.util.UUID;

/** 탈퇴(ACC-4). auth가 계정을 비활성화한다(docs/architecture/context-map.md "member.member"). */
public record MemberWithdrawn(UUID memberId) {

    public static final String TYPE = "MemberWithdrawn";
}
