package com.openmarket.review.command.domain;

/** 리뷰 저장소. 구현은 {@code infra}에 있다. */
public interface ReviewRepository {

    Review save(Review review);
}
