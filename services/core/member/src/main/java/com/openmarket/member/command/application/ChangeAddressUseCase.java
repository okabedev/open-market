package com.openmarket.member.command.application;

import com.openmarket.member.command.domain.Address;
import com.openmarket.member.command.domain.Member;
import com.openmarket.member.command.domain.MemberNotFoundException;
import com.openmarket.member.command.domain.MemberRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 배송지 내용을 고친다(ACC-3). */
@Service
public class ChangeAddressUseCase {

    private final MemberRepository members;
    private final Clock clock;

    public ChangeAddressUseCase(MemberRepository members, Clock clock) {
        this.members = members;
        this.clock = clock;
    }

    @Transactional
    public void execute(ChangeAddressCommand command) {
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
        member.changeAddress(command.addressId(), details, clock.instant());
    }
}
