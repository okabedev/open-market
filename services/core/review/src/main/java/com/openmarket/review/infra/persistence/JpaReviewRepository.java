package com.openmarket.review.infra.persistence;

import com.openmarket.review.command.domain.Review;
import com.openmarket.review.command.domain.ReviewRepository;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** {@link ReviewRepository}의 Spring Data JPA 구현. */
public interface JpaReviewRepository extends Repository<Review, UUID>, ReviewRepository {}
