package com.openmarket.fulfillment.infra.persistence;

import com.openmarket.fulfillment.command.domain.Claim;
import com.openmarket.fulfillment.command.domain.ClaimRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** {@link ClaimRepository}의 Spring Data JPA 구현. */
public interface JpaClaimRepository extends Repository<Claim, UUID>, ClaimRepository {

    /** {@code SELECT ... FOR UPDATE}. 환불 결과가 겹쳐 와도 한 번에 하나만 바꾼다. */
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Claim> findForUpdateById(UUID id);

    @Override
    @Query(
            value =
                    "SELECT EXISTS (SELECT 1 FROM claim WHERE seller_order_id = :sellerOrderId AND"
                            + " status IN ('REQUESTED', 'APPROVED', 'MEDIATING'))",
            nativeQuery = true)
    boolean existsOngoingBySellerOrderId(UUID sellerOrderId);
}
