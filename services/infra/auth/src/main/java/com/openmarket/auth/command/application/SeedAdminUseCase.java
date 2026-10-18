package com.openmarket.auth.command.application;

import com.openmarket.auth.command.domain.Account;
import com.openmarket.auth.command.domain.AccountRepository;
import com.openmarket.auth.command.domain.EmailAddress;
import com.openmarket.common.core.id.Ids;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 계정을 시드로 만든다. 같은 이메일 계정이 있으면 아무것도 바꾸지 않는다(멱등). 비밀번호를 바꾸려고 다시 실행해도 덮어쓰지 않는다.
 *
 * <p>여러 인스턴스가 동시에 실행하면 한쪽이 이메일 유니크 제약으로 실패한다. 기동 시 한 번만 돌리므로 재기동으로 충분하다.
 */
@Service
public class SeedAdminUseCase {

    private final AccountRepository accounts;
    private final PasswordHasherPort passwordHasher;
    private final Clock clock;

    public SeedAdminUseCase(
            AccountRepository accounts, PasswordHasherPort passwordHasher, Clock clock) {
        this.accounts = accounts;
        this.passwordHasher = passwordHasher;
        this.clock = clock;
    }

    /** 새로 만들었으면 {@code true}. */
    @Transactional
    public boolean execute(SeedAdminCommand command) {
        EmailAddress email = EmailAddress.of(command.email());
        if (accounts.findByEmail(email).isPresent()) {
            return false;
        }
        accounts.save(
                Account.seedAdmin(
                        Ids.newId(),
                        email,
                        passwordHasher.hash(command.password()),
                        clock.instant()));
        return true;
    }
}
