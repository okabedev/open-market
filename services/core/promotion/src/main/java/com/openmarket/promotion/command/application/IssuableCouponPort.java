package com.openmarket.promotion.command.application;

import com.openmarket.promotion.command.domain.Coupon;
import java.util.Optional;
import java.util.UUID;

/**
 * 선착순 발급이 Redis 게이트 앞에서 쓰는 쿠폰 조회. 구현은 짧게 캐시해도 된다: 조건은 바뀌지 않고, 상태는 발급 중에서 중지로만 바뀌며, 발급 수를 올리는 DB 갱신이
 * 상태와 기간을 다시 확인한다. 캐시가 늦으면 중지 직후 잠깐 차지했다가 되돌릴 뿐이다(docs/architecture/promotion.md "선착순 발급").
 *
 * <p>돌려준 쿠폰은 읽기만 한다.
 */
@FunctionalInterface
public interface IssuableCouponPort {

    Optional<Coupon> findById(UUID couponId);
}
