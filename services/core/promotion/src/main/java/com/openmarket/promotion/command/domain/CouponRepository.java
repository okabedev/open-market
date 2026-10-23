package com.openmarket.promotion.command.domain;

import java.util.Optional;
import java.util.UUID;

/** 쿠폰 정책 저장소. 구현은 {@code infra}에 있다. */
public interface CouponRepository {

    Coupon save(Coupon coupon);

    /** 쿠폰 행을 잠그고 읽는다. 중지가 겹쳐도 감사 로그가 한 번만 남는다. */
    Optional<Coupon> findForUpdateById(UUID id);
}
