package com.openmarket.auth.command.application;

import com.openmarket.auth.command.domain.Account;
import com.openmarket.auth.command.domain.AccountRepository;
import com.openmarket.common.core.error.NotFoundException;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴한 회원의 계정을 비활성화하고 인가 기록을 지운다(ACC-4). 남은 액세스 토큰은 수명(10분)이 지나면 끝나고, 리프레시 토큰으로는 갱신할 수 없다. 다시 와도 결과가
 * 같다(멱등).
 */
@Service
public class DeactivateAccountUseCase {

    private final AccountRepository accounts;
    private final PasswordHasherPort passwordHasher;
    private final AuthorizationRevokerPort authorizations;
    private final Clock clock;

    public DeactivateAccountUseCase(
            AccountRepository accounts,
            PasswordHasherPort passwordHasher,
            AuthorizationRevokerPort authorizations,
            Clock clock) {
        this.accounts = accounts;
        this.passwordHasher = passwordHasher;
        this.authorizations = authorizations;
        this.clock = clock;
    }

    @Transactional
    public void execute(DeactivateAccountCommand command) {
        Account account =
                accounts.findForUpdateById(command.accountId())
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                "AUTH_ACCOUNT_NOT_FOUND", "계정을 찾을 수 없습니다."));
        // 아무도 모르는 비밀번호의 해시. 비활성 계정은 비밀번호 확인 전에 거절되지만 해시도 쓸 수 없게 둔다.
        account.deactivate(passwordHasher.hash(UUID.randomUUID().toString()), clock.instant());
        authorizations.revokeAll(account.id());
    }
}
