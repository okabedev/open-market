package com.openmarket.catalog.infra.persistence;

import com.openmarket.catalog.command.domain.ProductAvailability;
import com.openmarket.catalog.command.domain.ProductAvailabilityRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.Repository;

/** {@link ProductAvailabilityRepository}의 Spring Data JPA 구현. */
public interface JpaProductAvailabilityRepository
        extends Repository<ProductAvailability, UUID>, ProductAvailabilityRepository {

    /** {@code SELECT ... FOR UPDATE}. */
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ProductAvailability> findForUpdateByProductId(UUID productId);
}
