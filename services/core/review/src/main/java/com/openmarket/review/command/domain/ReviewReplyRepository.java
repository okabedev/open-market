package com.openmarket.review.command.domain;

import java.util.Optional;
import java.util.UUID;

/** 답글 저장소. 구현은 {@code infra}에 있다. */
public interface ReviewReplyRepository {

    ReviewReply save(ReviewReply reply);

    Optional<ReviewReply> findById(UUID reviewId);
}
