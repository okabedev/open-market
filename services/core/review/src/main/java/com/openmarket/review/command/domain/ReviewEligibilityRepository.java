package com.openmarket.review.command.domain;

import java.util.Optional;
import java.util.UUID;

/** 리뷰 작성 자격 저장소. 구현은 {@code infra}에 있다. */
public interface ReviewEligibilityRepository {

    ReviewEligibility save(ReviewEligibility eligibility);

    boolean existsById(UUID orderLineId);

    /** 리뷰 작성. 같은 주문 항목의 작성이 동시에 오면 한쪽이 기다린다. */
    Optional<ReviewEligibility> findForUpdateById(UUID orderLineId);
}
