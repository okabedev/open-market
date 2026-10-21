package com.openmarket.order.infra.persistence;

import com.openmarket.order.command.domain.Order;
import com.openmarket.order.command.domain.OrderRepository;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** {@link OrderRepository}의 Spring Data JPA 구현. */
public interface JpaOrderRepository extends Repository<Order, UUID>, OrderRepository {

    /** {@code SELECT ... FOR UPDATE}. 예약 결과, 만료, 결제 반영이 겹쳐도 상태를 덮어쓰지 않는다. */
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Order> findForUpdateById(UUID id);

    @Override
    @Query(
            value =
                    "SELECT id FROM orders WHERE status IN ('PENDING', 'AWAITING_PAYMENT') AND"
                            + " placed_at <= :placedBefore ORDER BY placed_at LIMIT :limit",
            nativeQuery = true)
    List<UUID> findUnpaidIdsPlacedBefore(Instant placedBefore, int limit);
}
