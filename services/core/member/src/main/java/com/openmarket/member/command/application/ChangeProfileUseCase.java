package com.openmarket.member.command.application;

import com.openmarket.member.command.domain.Member;
import com.openmarket.member.command.domain.MemberNotFoundException;
import com.openmarket.member.command.domain.MemberRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 이름과 연락처를 바꾼다(ACC-3). */
@Service
public class ChangeProfileUseCase {

    private final MemberRepository members;
    private final Clock clock;

    public ChangeProfileUseCase(MemberRepository members, Clock clock) {
        this.members = members;
        this.clock = clock;
    }

    @Transactional
    public void execute(ChangeProfileCommand command) {
        Member member =
                members.findForUpdateById(command.memberId())
                        .orElseThrow(MemberNotFoundException::new);
        member.changeProfile(command.name(), command.phone(), clock.instant());
    }
}
