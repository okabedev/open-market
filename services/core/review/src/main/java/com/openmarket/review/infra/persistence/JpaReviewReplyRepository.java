package com.openmarket.review.infra.persistence;

import com.openmarket.review.command.domain.ReviewReply;
import com.openmarket.review.command.domain.ReviewReplyRepository;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** {@link ReviewReplyRepository}의 Spring Data JPA 구현. */
public interface JpaReviewReplyRepository
        extends Repository<ReviewReply, UUID>, ReviewReplyRepository {}
