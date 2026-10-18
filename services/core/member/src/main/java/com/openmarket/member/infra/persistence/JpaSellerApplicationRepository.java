package com.openmarket.member.infra.persistence;

import com.openmarket.member.command.domain.SellerApplication;
import com.openmarket.member.command.domain.SellerApplicationRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.Repository;

/** {@link SellerApplicationRepository}의 Spring Data JPA 구현. */
public interface JpaSellerApplicationRepository
        extends Repository<SellerApplication, UUID>, SellerApplicationRepository {

    /** {@code SELECT ... FOR UPDATE}. 두 관리자가 동시에 심사해도 결과는 하나다. */
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SellerApplication> findForUpdateById(UUID id);
}
