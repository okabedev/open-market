package com.openmarket.promotion.command.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 발급 쿠폰 저장소. 구현은 {@code infra}에 있다. */
public interface IssuedCouponRepository {

    /** 쿠폰 정책과 회원의 쌍이 없을 때만 넣는다({@code ON CONFLICT DO NOTHING}). 넣었으면 {@code true}. */
    boolean insertIfAbsent(IssuedCoupon issued);

    Optional<UUID> findIdByCouponIdAndMemberId(UUID couponId, UUID memberId);

    /** Redis 키를 다시 만들 때 쓴다. 이 쿠폰을 받은 회원 전부. */
    List<UUID> findMemberIdsByCouponId(UUID couponId);
}
