package com.openmarket.member.command.application;

import com.openmarket.member.command.domain.MemberNotFoundException;
import com.openmarket.member.command.domain.MemberRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원의 이메일 인증을 기록한다(auth {@code AccountEmailVerified}). 회원이 없으면 실패해서 리스너가 다시 시도한다. */
@Service
public class VerifyMemberEmailUseCase {

    private final MemberRepository members;
    private final Clock clock;

    public VerifyMemberEmailUseCase(MemberRepository members, Clock clock) {
        this.members = members;
        this.clock = clock;
    }

    @Transactional
    public void execute(VerifyMemberEmailCommand command) {
        members.findForUpdateById(command.memberId())
                .orElseThrow(MemberNotFoundException::new)
                .verifyEmail(clock.instant());
    }
}
