package com.openmarket.auth.infra.persistence;

import com.openmarket.auth.command.domain.Account;
import com.openmarket.auth.command.domain.AccountRepository;
import com.openmarket.auth.command.domain.EmailAddress;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.Repository;

/** {@link AccountRepository}의 Spring Data JPA 구현. 메서드는 도메인 인터페이스에서 이름으로 만든다. */
public interface JpaAccountRepository extends Repository<Account, UUID>, AccountRepository {

    /** {@code SELECT ... FOR UPDATE}. 로그인 실패를 동시에 세도 횟수를 잃지 않는다. */
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Account> findForUpdateByEmail(EmailAddress email);

    /** {@code SELECT ... FOR UPDATE}. */
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Account> findForUpdateById(UUID id);
}
