package com.openmarket.review.command.domain;

import java.time.Instant;
import java.util.UUID;

/** 상품 별점 요약 저장소. 구현은 {@code infra}에 있다. */
public interface RatingSummaryRepository {

    /**
     * 상품의 요약 행을 잠가서 준다({@code SELECT ... FOR UPDATE}). 없으면 빈 요약(리비전 0)을 먼저 넣는다({@code ON CONFLICT
     * DO NOTHING}). 같은 상품의 변경이 동시에 오면 한쪽이 기다린다.
     */
    RatingSummary lockOrCreate(UUID productId, Instant now);

    RatingSummary save(RatingSummary summary);
}
