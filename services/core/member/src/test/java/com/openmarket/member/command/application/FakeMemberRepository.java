package com.openmarket.member.command.application;

import com.openmarket.member.command.domain.Member;
import com.openmarket.member.command.domain.MemberRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** 메모리 저장소. 잠금 조회를 했는지 센다. */
class FakeMemberRepository implements MemberRepository {

    final Map<UUID, Member> members = new HashMap<>();
    int lockedReads;

    @Override
    public boolean existsById(UUID id) {
        return members.containsKey(id);
    }

    @Override
    public Optional<Member> findById(UUID id) {
        return Optional.ofNullable(members.get(id));
    }

    @Override
    public Optional<Member> findForUpdateById(UUID id) {
        lockedReads++;
        return findById(id);
    }

    @Override
    public Member save(Member member) {
        members.put(member.id(), member);
        return member;
    }
}
