package com.openmarket.member.command.domain;

import java.util.Optional;
import java.util.UUID;

/** 회원 저장소. 구현은 {@code infra}에 있다. */
public interface MemberRepository {

    boolean existsById(UUID id);

    Optional<Member> findById(UUID id);

    /** 배송지를 바꿀 때 쓴다. 동시에 추가해도 개수 제한과 기본 배송지 규칙이 지켜지도록 회원 행을 잠근다. */
    Optional<Member> findForUpdateById(UUID id);

    Member save(Member member);
}
