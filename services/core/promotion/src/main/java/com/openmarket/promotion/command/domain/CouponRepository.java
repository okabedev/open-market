package com.openmarket.promotion.command.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** 쿠폰 정책 저장소. 구현은 {@code infra}에 있다. */
public interface CouponRepository {

    Coupon save(Coupon coupon);

    Optional<Coupon> findById(UUID id);

    /** 쿠폰 행을 잠그고 읽는다. 중지가 겹쳐도 감사 로그가 한 번만 남는다. */
    Optional<Coupon> findForUpdateById(UUID id);

    /** 지금 DB에 있는 상태. 영속성 컨텍스트에 읽어 둔 엔티티가 아니라 행을 다시 읽는다. */
    Optional<CouponStatus> findStatusById(UUID id);

    /**
     * 발급 수를 1 올린다. 발급 중이고 {@code now}가 발급 기간 안이고 발급 수가 수량보다 작을 때만 올리고 {@code true}다. 한 행의 조건부
     * UPDATE라 동시에 불러도 수량을 넘지 않는다(R11의 마지막 방어선).
     */
    boolean incrementIssuedCount(UUID id, Instant now);
}
