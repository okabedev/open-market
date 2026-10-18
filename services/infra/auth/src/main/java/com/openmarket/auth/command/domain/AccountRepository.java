package com.openmarket.auth.command.domain;

import java.util.Optional;
import java.util.UUID;

/** 계정 저장소. 구현은 {@code infra}에 있다. */
public interface AccountRepository {

    Optional<Account> findById(UUID id);

    Optional<Account> findByEmail(EmailAddress email);

    Account save(Account account);
}
