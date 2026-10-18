package com.openmarket.member.command.application;

import com.openmarket.member.command.domain.Member;
import com.openmarket.member.command.domain.MemberNotFoundException;
import com.openmarket.member.command.domain.MemberRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 배송지를 지운다(ACC-3). 이미 없으면 그대로다(멱등). */
@Service
public class RemoveAddressUseCase {

    private final MemberRepository members;
    private final Clock clock;

    public RemoveAddressUseCase(MemberRepository members, Clock clock) {
        this.members = members;
        this.clock = clock;
    }

    @Transactional
    public void execute(RemoveAddressCommand command) {
        Member member =
                members.findForUpdateById(command.memberId())
                        .orElseThrow(MemberNotFoundException::new);
        member.removeAddress(command.addressId(), clock.instant());
    }
}
