package com.openmarket.settlement.infra.persistence;

import com.openmarket.settlement.command.domain.CommissionRate;
import com.openmarket.settlement.command.domain.CommissionRateRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** {@link CommissionRateRepository}의 Spring Data JPA 구현. */
public interface JpaCommissionRateRepository
        extends Repository<CommissionRate, UUID>, CommissionRateRepository {

    /** {@code SELECT ... FOR UPDATE}. */
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CommissionRate c where c.categoryId = :categoryId")
    Optional<CommissionRate> findForUpdateById(@Param("categoryId") UUID categoryId);
}
