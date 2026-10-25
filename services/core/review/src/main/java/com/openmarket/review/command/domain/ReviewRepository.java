package com.openmarket.review.command.domain;

import java.util.Optional;
import java.util.UUID;

/** 리뷰 저장소. 구현은 {@code infra}에 있다. */
public interface ReviewRepository {

    Review save(Review review);

    /** 답글, 숨김·보임. 같은 리뷰를 동시에 바꾸면 한쪽이 기다린다. */
    Optional<Review> findForUpdateById(UUID reviewId);
}
