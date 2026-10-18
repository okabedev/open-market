package com.openmarket.member.query.dto;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 본인 프로필. 본인에게는 원문을 준다. 다른 사람에게 보이는 응답과 로그에서는 마스킹한다(NFR-7, docs/prd.md).
 *
 * @param emailVerified 이메일 인증을 마쳤는지
 * @param joinedAt 가입 시각
 */
public record MemberView(
        UUID memberId,
        String email,
        String name,
        @Nullable String phone,
        boolean emailVerified,
        Instant joinedAt) {

    /** QueryDSL 프로젝션용. 인증 시각이 있으면 인증한 것이다. */
    public MemberView(
            UUID memberId,
            String email,
            String name,
            @Nullable String phone,
            @Nullable Instant emailVerifiedAt,
            Instant joinedAt) {
        this(memberId, email, name, phone, emailVerifiedAt != null, joinedAt);
    }

    /** 개인정보를 남기지 않는다(NFR-7). */
    @Override
    public String toString() {
        return "MemberView[memberId=" + memberId + "]";
    }
}
