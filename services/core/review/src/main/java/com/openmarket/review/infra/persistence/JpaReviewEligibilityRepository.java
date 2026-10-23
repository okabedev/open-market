package com.openmarket.review.infra.persistence;

import com.openmarket.review.command.domain.ReviewEligibility;
import com.openmarket.review.command.domain.ReviewEligibilityRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** {@link ReviewEligibilityRepository}의 Spring Data JPA 구현. */
public interface JpaReviewEligibilityRepository
        extends Repository<ReviewEligibility, UUID>, ReviewEligibilityRepository {

    /** {@code SELECT ... FOR UPDATE}. */
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from ReviewEligibility e where e.orderLineId = :orderLineId")
    Optional<ReviewEligibility> findForUpdateById(@Param("orderLineId") UUID orderLineId);
}
