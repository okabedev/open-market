package com.openmarket.auth.command.application;

import com.openmarket.auth.command.domain.Account;
import com.openmarket.auth.command.domain.AccountRepository;
import com.openmarket.auth.command.domain.Role;
import com.openmarket.common.core.error.NotFoundException;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code SELLER} 역할을 붙이거나 뗀다(SEL-2, SEL-3). 이미 그 상태면 아무것도 하지 않는다(멱등). 토큰의 {@code roles}는 발급할 때마다
 * DB에서 읽으므로 다음 토큰 갱신부터 반영된다.
 */
@Service
public class ChangeSellerRoleUseCase {

    private final AccountRepository accounts;
    private final Clock clock;

    public ChangeSellerRoleUseCase(AccountRepository accounts, Clock clock) {
        this.accounts = accounts;
        this.clock = clock;
    }

    @Transactional
    public void execute(ChangeSellerRoleCommand command) {
        Account account =
                accounts.findForUpdateById(command.accountId())
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                "AUTH_ACCOUNT_NOT_FOUND", "계정을 찾을 수 없습니다."));
        if (command.seller()) {
            account.grantRole(Role.SELLER, clock.instant());
        } else {
            account.revokeRole(Role.SELLER, clock.instant());
        }
    }
}
