package com.openmarket.promotion.command.application;

import com.openmarket.promotion.command.application.CouponIssueGatePort.Claim;
import com.openmarket.promotion.command.domain.Coupon;
import com.openmarket.promotion.command.domain.CouponNotFoundException;
import com.openmarket.promotion.command.domain.CouponNotIssuingException;
import com.openmarket.promotion.command.domain.CouponRepository;
import com.openmarket.promotion.command.domain.CouponSoldOutException;
import com.openmarket.promotion.command.domain.CouponStatus;
import com.openmarket.promotion.command.domain.IssuedCoupon;
import com.openmarket.promotion.command.domain.IssuedCouponRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * 선착순 쿠폰을 내려받는다(PRM-2). 규칙은 docs/architecture/promotion.md "선착순 발급".
 *
 * <ol>
 *   <li>쿠폰이 지금 발급 중이어야 한다(409 {@code PROMOTION_COUPON_NOT_ISSUING}).
 *   <li>Redis 게이트에서 1인 1매와 수량을 차지한다. 키가 없으면 DB 발급 행으로 만든다. 매진이면 DB에 가지 않는다.
 *   <li>DB 트랜잭션에서 발급 수를 조건부로 올리고 발급 쿠폰을 넣는다. 수량과 1인 1매는 여기서 최종으로 정해진다. 실패하면 Redis에서 차지한 것을 되돌린다.
 * </ol>
 *
 * Redis 호출은 트랜잭션 밖에서 해서 DB 연결을 잡고 기다리지 않는다. 앞의 쿠폰 조회도 캐시해서({@link IssuableCouponPort}) 매진 응답은 DB에
 * 가지 않는다. 이미 받은 회원은 같은 발급 쿠폰을 받는다(다시 눌러도 같은 결과).
 */
@Service
public class IssueCouponUseCase {

    /** Redis 키는 발급 기간이 끝나고 이만큼 뒤에 사라진다. */
    static final Duration KEY_GRACE = Duration.ofHours(1);

    private final CouponRepository coupons;
    private final IssuableCouponPort issuableCoupons;
    private final IssuedCouponRepository issuedCoupons;
    private final CouponIssueGatePort gate;
    private final TransactionOperations transaction;
    private final Clock clock;

    public IssueCouponUseCase(
            CouponRepository coupons,
            IssuableCouponPort issuableCoupons,
            IssuedCouponRepository issuedCoupons,
            CouponIssueGatePort gate,
            TransactionOperations transaction,
            Clock clock) {
        this.coupons = coupons;
        this.issuableCoupons = issuableCoupons;
        this.issuedCoupons = issuedCoupons;
        this.gate = gate;
        this.transaction = transaction;
        this.clock = clock;
    }

    public Result execute(IssueCouponCommand command) {
        // 짧게 캐시한 쿠폰일 수 있다. 상태와 기간은 저장할 때 DB가 다시 확인한다.
        Coupon coupon =
                issuableCoupons
                        .findById(command.couponId())
                        .orElseThrow(CouponNotFoundException::new);
        IssuedCoupon candidate = IssuedCoupon.issue(coupon, command.memberId(), clock.instant());
        UUID couponId = coupon.id();
        int quantity = coupon.terms().quantity();
        Instant keysExpireAt = coupon.terms().issueEndAt().plus(KEY_GRACE);

        Claim claim = gate.claim(couponId, command.memberId(), quantity, keysExpireAt);
        if (claim == Claim.NOT_LOADED) {
            // Redis 재시작이나 처음 발급. DB 발급 행으로 키를 만들면서 차지한다.
            claim =
                    gate.loadAndClaim(
                            couponId,
                            issuedCoupons.findMemberIdsByCouponId(couponId),
                            command.memberId(),
                            quantity,
                            keysExpireAt);
        }
        return switch (claim) {
            case CLAIMED -> save(coupon, candidate, true);
            case ALREADY_CLAIMED ->
                    // 집합에 있는데 DB 행이 없으면 차지한 요청이 저장 전에 끝났다. 발급 수는 이미 셌으므로 DB에만 저장한다.
                    issuedCoupons
                            .findIdByCouponIdAndMemberId(couponId, command.memberId())
                            .map(id -> new Result(id, false))
                            .orElseGet(() -> save(coupon, candidate, false));
            case SOLD_OUT -> throw new CouponSoldOutException();
            case NOT_LOADED -> throw new IllegalStateException("loadAndClaim은 NOT_LOADED를 주지 않는다");
        };
    }

    /**
     * DB에 저장한다. {@code claimed}면 이 요청이 Redis에서 차지했으므로 실패할 때 되돌린다.
     *
     * @return 새로 만들었으면 {@code created}
     */
    private Result save(Coupon coupon, IssuedCoupon candidate, boolean claimed) {
        UUID couponId = coupon.id();
        UUID memberId = candidate.memberId();
        @Nullable UUID existing;
        try {
            existing = transaction.execute(status -> persist(candidate));
        } catch (CouponSoldOutException e) {
            // Redis가 적게 셌다(키를 다시 만드는 사이 끼어든 요청 등). 다음 요청부터 Redis에서 거르게 맞춘다.
            if (claimed) {
                gate.release(couponId, memberId);
            }
            gate.markSoldOut(couponId, coupon.terms().quantity());
            throw e;
        } catch (ConcurrentlyIssuedException e) {
            existing =
                    issuedCoupons
                            .findIdByCouponIdAndMemberId(couponId, memberId)
                            .orElseThrow(() -> e);
        } catch (RuntimeException e) {
            if (claimed) {
                gate.release(couponId, memberId);
            }
            throw e;
        }
        if (existing == null) {
            return new Result(candidate.id(), true);
        }
        // 이미 받은 회원이 Redis 집합에서 빠져 있어 다시 차지했다. 회원은 집합에 두고 발급 수만 되돌린다.
        if (claimed) {
            gate.releaseStock(couponId);
        }
        return new Result(existing, false);
    }

    /** 트랜잭션 안. 이미 받았으면 그 발급 쿠폰 ID, 새로 넣었으면 null. */
    private @Nullable UUID persist(IssuedCoupon candidate) {
        Optional<UUID> existing =
                issuedCoupons.findIdByCouponIdAndMemberId(
                        candidate.couponId(), candidate.memberId());
        if (existing.isPresent()) {
            return existing.get();
        }
        if (!coupons.incrementIssuedCount(candidate.couponId(), candidate.issuedAt())) {
            CouponStatus status =
                    coupons.findStatusById(candidate.couponId()).orElse(CouponStatus.STOPPED);
            throw status == CouponStatus.ISSUING
                    ? new CouponSoldOutException()
                    : new CouponNotIssuingException();
        }
        if (!issuedCoupons.insertIfAbsent(candidate)) {
            // 같은 회원의 다른 요청이 먼저 넣었다. 올린 발급 수를 롤백한다.
            throw new ConcurrentlyIssuedException();
        }
        return null;
    }

    /**
     * 내려받은 발급 쿠폰.
     *
     * @param created 새로 발급했으면 {@code true}(201), 이미 받은 쿠폰이면 {@code false}(200)
     */
    public record Result(UUID issuedCouponId, boolean created) {}

    /** 같은 회원의 요청이 동시에 저장했다. 트랜잭션을 롤백하려고 던진다. */
    private static final class ConcurrentlyIssuedException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        ConcurrentlyIssuedException() {
            super(null, null, false, false);
        }
    }
}
