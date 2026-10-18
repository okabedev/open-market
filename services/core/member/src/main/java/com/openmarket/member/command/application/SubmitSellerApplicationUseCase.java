package com.openmarket.member.command.application;

import com.openmarket.common.core.id.Ids;
import com.openmarket.common.core.privacy.Masking;
import com.openmarket.member.command.domain.AlreadySellerException;
import com.openmarket.member.command.domain.EmailNotVerifiedException;
import com.openmarket.member.command.domain.Member;
import com.openmarket.member.command.domain.MemberNotFoundException;
import com.openmarket.member.command.domain.MemberRepository;
import com.openmarket.member.command.domain.SellerApplication;
import com.openmarket.member.command.domain.SellerApplicationPendingException;
import com.openmarket.member.command.domain.SellerApplicationRepository;
import com.openmarket.member.command.domain.SellerApplicationStatus;
import com.openmarket.member.command.domain.SellerRepository;
import com.openmarket.member.command.domain.SettlementAccount;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 입점을 신청한다(SEL-1). 이메일 인증을 마친 회원만 신청할 수 있고, 판매자이거나 심사 중인 신청이 있으면 실패한다. 회원 행을 잠가 동시에 두 번 신청해도 하나만
 * 남는다.
 */
@Service
public class SubmitSellerApplicationUseCase {

    private final MemberRepository members;
    private final SellerApplicationRepository applications;
    private final SellerRepository sellers;
    private final AccountNumberCipherPort cipher;
    private final Clock clock;

    public SubmitSellerApplicationUseCase(
            MemberRepository members,
            SellerApplicationRepository applications,
            SellerRepository sellers,
            AccountNumberCipherPort cipher,
            Clock clock) {
        this.members = members;
        this.applications = applications;
        this.sellers = sellers;
        this.cipher = cipher;
        this.clock = clock;
    }

    @Transactional
    public UUID execute(SubmitSellerApplicationCommand command) {
        SellerApplication.Business business =
                new SellerApplication.Business(
                        command.storeName(),
                        command.businessNumber(),
                        command.representativeName());
        String accountNumber = SettlementAccount.normalizeNumber(command.accountNumber());
        SettlementAccount account =
                new SettlementAccount(
                        command.bankName(),
                        command.accountHolder(),
                        cipher.encrypt(accountNumber),
                        Masking.accountNumber(accountNumber));

        Member member =
                members.findForUpdateById(command.memberId())
                        .orElseThrow(MemberNotFoundException::new);
        if (!member.isEmailVerified()) {
            throw new EmailNotVerifiedException();
        }
        if (sellers.existsById(member.id())) {
            throw new AlreadySellerException();
        }
        if (applications.existsByMemberIdAndStatus(
                member.id(), SellerApplicationStatus.SUBMITTED)) {
            throw new SellerApplicationPendingException();
        }
        return applications
                .save(
                        SellerApplication.submit(
                                Ids.newId(), member.id(), business, account, clock.instant()))
                .id();
    }
}
