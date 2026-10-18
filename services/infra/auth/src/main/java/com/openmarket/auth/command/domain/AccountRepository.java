package com.openmarket.auth.command.domain;

import java.util.Optional;
import java.util.UUID;

/** 계정 저장소. 구현은 {@code infra}에 있다. */
public interface AccountRepository {

    Optional<Account> findById(UUID id);

    Optional<Account> findByEmail(EmailAddress email);

    /** 로그인 실패를 셀 때 쓴다. 동시에 실패해도 횟수를 잃지 않도록 행을 잠근다. */
    Optional<Account> findForUpdateByEmail(EmailAddress email);

    /** 역할을 바꿀 때 쓴다. 로그인 기록과 겹쳐도 변경을 잃지 않도록 행을 잠근다. */
    Optional<Account> findForUpdateById(UUID id);

    Account save(Account account);
}
