package com.openmarket.review.infra.persistence;

import com.openmarket.review.command.domain.Review;
import com.openmarket.review.command.domain.ReviewRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** {@link ReviewRepository}의 Spring Data JPA 구현. */
public interface JpaReviewRepository extends Repository<Review, UUID>, ReviewRepository {

    /** {@code SELECT ... FOR UPDATE}. */
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Review r where r.id = :reviewId")
    Optional<Review> findForUpdateById(@Param("reviewId") UUID reviewId);
}
