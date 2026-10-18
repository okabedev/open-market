package com.openmarket.member.command.application;

import com.openmarket.common.messaging.outbox.Outbox;
import com.openmarket.member.command.domain.Member;
import com.openmarket.member.command.domain.MemberNotFoundException;
import com.openmarket.member.command.domain.MemberRepository;
import com.openmarket.member.command.domain.MemberWithdrawn;
import com.openmarket.member.command.domain.OngoingOrdersException;
import com.openmarket.member.command.domain.SellerApplicationRepository;
import com.openmarket.member.command.domain.SellerCannotWithdrawException;
import com.openmarket.member.command.domain.SellerRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴한다(ACC-4). 판매자이거나 진행 중인 주문이 있으면 실패한다. 회원과 입점 신청의 개인정보를 바로 가명 처리하고 {@code MemberWithdrawn}을 쌓는다.
 * auth가 받아 계정을 비활성화한다. 이미 탈퇴했으면 아무것도 하지 않는다(멱등).
 */
@Service
public class WithdrawMemberUseCase {

    static final String TOPIC = "member.member";

    private final MemberRepository members;
    private final SellerApplicationRepository applications;
    private final SellerRepository sellers;
    private final OngoingOrderPort orders;
    private final Outbox outbox;
    private final Clock clock;

    public WithdrawMemberUseCase(
            MemberRepository members,
            SellerApplicationRepository applications,
            SellerRepository sellers,
            OngoingOrderPort orders,
            Outbox outbox,
            Clock clock) {
        this.members = members;
        this.applications = applications;
        this.sellers = sellers;
        this.orders = orders;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public void execute(WithdrawMemberCommand command) {
        Member member =
                members.findForUpdateById(command.memberId())
                        .orElseThrow(MemberNotFoundException::new);
        if (member.isWithdrawn()) {
            return;
        }
        if (sellers.existsById(member.id())) {
            throw new SellerCannotWithdrawException();
        }
        if (orders.hasOngoingOrders(member.id())) {
            throw new OngoingOrdersException();
        }
        Instant now = clock.instant();
        member.withdraw(now);
        applications.findAllByMemberId(member.id()).forEach(a -> a.withdrawApplicant(now));
        outbox.append(
                TOPIC,
                member.id().toString(),
                MemberWithdrawn.TYPE,
                new MemberWithdrawn(member.id()));
    }
}
