package com.openmarket.catalog.infra.persistence;

import com.openmarket.catalog.command.domain.StockReservation;
import com.openmarket.catalog.command.domain.StockReservationRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.Repository;

/** {@link StockReservationRepository}의 Spring Data JPA 구현. */
public interface JpaStockReservationRepository
        extends Repository<StockReservation, UUID>, StockReservationRepository {

    /** {@code SELECT ... FOR UPDATE}. 같은 주문의 확정과 해제가 겹치지 않는다. */
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<StockReservation> findForUpdateByOrderId(UUID orderId);
}
