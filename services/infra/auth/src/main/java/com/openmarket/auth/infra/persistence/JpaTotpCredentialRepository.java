package com.openmarket.auth.infra.persistence;

import com.openmarket.auth.command.domain.TotpCredential;
import com.openmarket.auth.command.domain.TotpCredentialRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.Repository;

/** {@link TotpCredentialRepository}의 Spring Data JPA 구현. */
public interface JpaTotpCredentialRepository
        extends Repository<TotpCredential, UUID>, TotpCredentialRepository {

    /** {@code SELECT ... FOR UPDATE}. 같은 코드가 동시에 와도 한 번만 받는다. */
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TotpCredential> findForUpdateByAccountId(UUID accountId);
}
