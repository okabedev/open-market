package com.openmarket.promotion.command.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 발급 쿠폰 저장소. 구현은 {@code infra}에 있다. */
public interface IssuedCouponRepository {

    IssuedCoupon save(IssuedCoupon issued);

    /** 쿠폰 정책과 회원의 쌍이 없을 때만 넣는다({@code ON CONFLICT DO NOTHING}). 넣었으면 {@code true}. */
    boolean insertIfAbsent(IssuedCoupon issued);

    Optional<UUID> findIdByCouponIdAndMemberId(UUID couponId, UUID memberId);

    /** Redis 키를 다시 만들 때 쓴다. 이 쿠폰을 받은 회원 전부. */
    List<UUID> findMemberIdsByCouponId(UUID couponId);

    /** 할인 계산. 잠그지 않는다. 없는 ID는 빠진다. */
    List<IssuedCoupon> findAllByIdIn(Collection<UUID> ids);

    /** 주문 쿠폰 예약. ID 순서로 잠근다(같은 쿠폰을 쓰는 두 주문이 서로 기다리지 않게). 없는 ID는 빠진다. */
    List<IssuedCoupon> findForUpdateByIdInOrderById(Collection<UUID> ids);

    /** 사용·복원. 그 주문에 묶인 쿠폰을 ID 순서로 잠근다. */
    List<IssuedCoupon> findForUpdateByOrderIdOrderById(UUID orderId);
}
