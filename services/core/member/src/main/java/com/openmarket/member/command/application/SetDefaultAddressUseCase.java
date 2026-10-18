package com.openmarket.member.command.application;

import com.openmarket.member.command.domain.Member;
import com.openmarket.member.command.domain.MemberNotFoundException;
import com.openmarket.member.command.domain.MemberRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 기본 배송지를 바꾼다(ACC-3). 이미 기본이면 그대로다. */
@Service
public class SetDefaultAddressUseCase {

    private final MemberRepository members;
    private final Clock clock;

    public SetDefaultAddressUseCase(MemberRepository members, Clock clock) {
        this.members = members;
        this.clock = clock;
    }

    @Transactional
    public void execute(SetDefaultAddressCommand command) {
        Member member =
                members.findForUpdateById(command.memberId())
                        .orElseThrow(MemberNotFoundException::new);
        member.makeDefaultAddress(command.addressId(), clock.instant());
    }
}
