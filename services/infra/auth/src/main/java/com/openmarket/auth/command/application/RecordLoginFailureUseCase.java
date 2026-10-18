package com.openmarket.auth.command.application;

import com.openmarket.auth.command.domain.AccountRepository;
import com.openmarket.auth.command.domain.EmailAddress;
import com.openmarket.auth.command.domain.InvalidEmailException;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 비밀번호가 틀린 로그인을 센다. 5회 연속이면 계정을 잠근다(ACC-1). 행을 잠그고 세어 동시에 실패해도 횟수를 잃지 않는다. */
@Service
public class RecordLoginFailureUseCase {

    private final AccountRepository accounts;
    private final Clock clock;

    public RecordLoginFailureUseCase(AccountRepository accounts, Clock clock) {
        this.accounts = accounts;
        this.clock = clock;
    }

    @Transactional
    public void execute(RecordLoginFailureCommand command) {
        EmailAddress email;
        try {
            email = EmailAddress.of(command.email());
        } catch (InvalidEmailException e) {
            return;
        }
        accounts.findForUpdateByEmail(email)
                .ifPresent(account -> account.recordLoginFailure(clock.instant()));
    }
}
