package com.openmarket.auth.command.application;

import com.openmarket.auth.command.domain.Account;
import com.openmarket.auth.command.domain.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 로그인에 성공하면 연속 실패 횟수를 지운다. 바뀐 값이 없으면 JPA가 UPDATE하지 않는다. */
@Service
public class RecordLoginSuccessUseCase {

    private final AccountRepository accounts;

    public RecordLoginSuccessUseCase(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional
    public void execute(RecordLoginSuccessCommand command) {
        accounts.findById(command.accountId()).ifPresent(Account::recordLoginSuccess);
    }
}
