package com.openmarket.settlement.infra.persistence;

import com.openmarket.settlement.command.domain.Settlement;
import com.openmarket.settlement.command.domain.SettlementRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** {@link SettlementRepository}의 Spring Data JPA 구현. */
public interface JpaSettlementRepository
        extends Repository<Settlement, UUID>, SettlementRepository {

    /** {@code SELECT ... FOR UPDATE}. */
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Settlement s where s.id = :settlementId")
    Optional<Settlement> findForUpdateById(@Param("settlementId") UUID settlementId);
}
