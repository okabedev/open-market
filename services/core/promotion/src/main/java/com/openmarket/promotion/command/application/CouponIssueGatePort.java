package com.openmarket.promotion.command.application;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

/**
 * 선착순 발급의 앞단 게이트(Redis). 쿠폰마다 발급한 회원 집합과 발급 수를 두고, 1인 1매와 수량을 원자적으로 확인하고 차지한다. DB까지 가는 요청을 수량 근처로
 * 줄이는 것이 목적이다. 어긋나도 DB 제약이 수량과 1인 1매를 지킨다(docs/architecture/promotion.md "선착순 발급", R11).
 *
 * <p>{@link #claim}과 {@link #loadAndClaim}은 Redis를 부를 수 없으면 {@link #unavailable}(503 {@code
 * PROMOTION_UNAVAILABLE})을 던진다. DB만으로 발급하지 않는다. 되돌리는 메서드는 실패해도 던지지 않는다(남은 차이는 발급 수를 많게 세는 쪽이라 초과
 * 발급이 되지 않는다).
 */
public interface CouponIssueGatePort {

    /**
     * 회원이 집합에 없고 발급 수가 수량보다 작으면 집합에 넣고 발급 수를 1 올린다. 키는 {@code keysExpireAt}에 사라진다.
     *
     * @return 키가 없으면 {@link Claim#NOT_LOADED}({@link #loadAndClaim}으로 다시 한다)
     */
    Claim claim(UUID couponId, UUID memberId, int quantity, Instant keysExpireAt);

    /**
     * DB 발급 행으로 키를 만들고 이어서 {@link #claim}한다. 발급 수는 회원 수다. 발급 수를 두는 것과 차지가 한 스크립트라 그 사이 키가 사라지지 않는다.
     * 다른 요청이 먼저 만들었으면 만든 키를 그대로 쓴다. {@link Claim#NOT_LOADED}를 돌려주지 않는다.
     */
    Claim loadAndClaim(
            UUID couponId,
            Collection<UUID> issuedMemberIds,
            UUID memberId,
            int quantity,
            Instant keysExpireAt);

    /** {@link Claim#CLAIMED}를 되돌린다. 집합에서 빼고 발급 수를 1 내린다. */
    void release(UUID couponId, UUID memberId);

    /** 발급 수만 1 내린다. 이미 발급한 회원이 다시 차지했을 때 쓴다(집합에는 그대로 둔다). */
    void releaseStock(UUID couponId);

    /** DB가 매진이라고 했을 때 Redis도 매진으로 맞춘다. 발급 수를 수량 이상으로 둔다. */
    void markSoldOut(UUID couponId, int quantity);

    /** 게이트를 부를 수 없다. 503 {@code PROMOTION_UNAVAILABLE}. */
    static BusinessException unavailable(Throwable cause) {
        return new BusinessException(
                ErrorType.UNAVAILABLE,
                "PROMOTION_UNAVAILABLE",
                "쿠폰 발급을 잠시 할 수 없습니다. 잠시 뒤 다시 시도해 주세요.",
                cause);
    }

    enum Claim {
        CLAIMED,
        /** 이미 집합에 있다. 차지하지 않았다. */
        ALREADY_CLAIMED,
        SOLD_OUT,
        NOT_LOADED
    }
}
