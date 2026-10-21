package com.openmarket.payment.infra.persistence;

import com.openmarket.payment.command.domain.Payment;
import com.openmarket.payment.command.domain.PaymentRepository;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** {@link PaymentRepository}의 Spring Data JPA 구현. */
public interface JpaPaymentRepository extends Repository<Payment, UUID>, PaymentRepository {

    /** {@code SELECT ... FOR UPDATE}. 승인, 재조회, 취소가 겹쳐도 한 번에 하나만 상태를 바꾼다. */
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Payment> findForUpdateById(UUID id);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Payment> findForUpdateByOrderId(UUID orderId);

    @Override
    @Query(
            value =
                    "SELECT id FROM payment WHERE status = 'IN_PROGRESS' AND requested_at <="
                            + " :requestedBefore ORDER BY requested_at LIMIT :limit",
            nativeQuery = true)
    List<UUID> findInProgressIdsRequestedBefore(Instant requestedBefore, int limit);

    @Override
    @Query(
            value =
                    "SELECT id FROM payment WHERE status = 'APPROVED' AND cancel_requested_at IS NOT"
                            + " NULL AND cancel_failure_code IS NULL ORDER BY cancel_requested_at"
                            + " LIMIT :limit",
            nativeQuery = true)
    List<UUID> findCancelRequestedApprovedIds(int limit);
}
