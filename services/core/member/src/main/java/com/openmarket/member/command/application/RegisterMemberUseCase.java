package com.openmarket.member.command.application;

import com.openmarket.member.command.domain.Member;
import com.openmarket.member.command.domain.MemberRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원을 만든다(auth {@code AccountRegistered}). 이미 있으면 아무것도 하지 않는다(멱등). */
@Service
public class RegisterMemberUseCase {

    private final MemberRepository members;
    private final Clock clock;

    public RegisterMemberUseCase(MemberRepository members, Clock clock) {
        this.members = members;
        this.clock = clock;
    }

    @Transactional
    public void execute(RegisterMemberCommand command) {
        if (members.existsById(command.memberId())) {
            return;
        }
        members.save(
                Member.register(
                        command.memberId(), command.email(), command.name(), clock.instant()));
    }
}
