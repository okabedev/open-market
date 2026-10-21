package com.openmarket.payment.infra.persistence;

import com.openmarket.payment.command.domain.Refund;
import com.openmarket.payment.command.domain.RefundRepository;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** {@link RefundRepository}의 Spring Data JPA 구현. */
public interface JpaRefundRepository extends Repository<Refund, UUID>, RefundRepository {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Refund> findForUpdateById(UUID id);

    @Override
    @Query(
            value =
                    "SELECT coalesce(sum(amount), 0) FROM refund WHERE payment_id = :paymentId AND"
                            + " status = 'REQUESTED'",
            nativeQuery = true)
    long sumRequestedAmountByPaymentId(UUID paymentId);

    @Override
    @Query(
            value =
                    "SELECT id FROM refund WHERE status = 'REQUESTED' ORDER BY requested_at LIMIT"
                            + " :limit",
            nativeQuery = true)
    List<UUID> findRequestedIds(int limit);
}
