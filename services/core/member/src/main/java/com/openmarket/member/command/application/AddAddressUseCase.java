package com.openmarket.member.command.application;

import com.openmarket.common.core.id.Ids;
import com.openmarket.member.command.domain.Address;
import com.openmarket.member.command.domain.Member;
import com.openmarket.member.command.domain.MemberNotFoundException;
import com.openmarket.member.command.domain.MemberRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 배송지를 더하고 새 배송지 ID를 돌려준다(ACC-3). 10개를 넘으면 실패한다. */
@Service
public class AddAddressUseCase {

    private final MemberRepository members;
    private final Clock clock;

    public AddAddressUseCase(MemberRepository members, Clock clock) {
        this.members = members;
        this.clock = clock;
    }

    @Transactional
    public UUID execute(AddAddressCommand command) {
        Address.Details details =
                new Address.Details(
                        command.label(),
                        command.recipientName(),
                        command.recipientPhone(),
                        command.zipCode(),
                        command.baseAddress(),
                        command.detailAddress());
        Member member =
                members.findForUpdateById(command.memberId())
                        .orElseThrow(MemberNotFoundException::new);
        return member.addAddress(Ids.newId(), details, command.asDefault(), clock.instant()).id();
    }
}
