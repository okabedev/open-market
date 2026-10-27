package com.openmarket.promotion.infra;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Ticker;
import com.openmarket.promotion.command.application.IssuableCouponPort;
import com.openmarket.promotion.command.domain.Coupon;
import com.openmarket.promotion.command.domain.CouponRepository;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 발급용 쿠폰 조회를 메모리에 둔다. 발급 요청이 몰리면 요청마다 쿠폰을 읽느라 DB 연결 앞에 줄을 서고, Redis에서 끝나는 매진 응답까지
 * 늦어졌다(docs/architecture/risks.md "부하 측정").
 *
 * <ul>
 *   <li>같은 쿠폰을 동시에 처음 찾으면 한 요청만 DB를 읽고 나머지는 그 결과를 기다린다.
 *   <li>{@link #REFRESH}가 지나면 지금 값을 주면서 뒤에서 새로 읽는다. 만료로 버리면 다시 읽는 동안 모든 요청이 기다리는데, 그때 DB 연결은 발급 저장
 *       트랜잭션들이 쥐고 있어 수 초씩 멈췄다.
 *   <li>새로 읽지 못한 채 {@link #EXPIRE}가 지나면 버린다.
 *   <li>없는 쿠폰은 두지 않는다.
 * </ul>
 */
@Component
class CachedIssuableCouponAdapter implements IssuableCouponPort {

    /** 중지가 발급 앞단에 보이기까지 걸리는 보통 시간. 그 사이 요청은 DB 갱신이 막는다. */
    static final Duration REFRESH = Duration.ofSeconds(1);

    /** 새로 읽기가 계속 실패할 때 오래된 쿠폰을 쓰는 최대 시간. */
    static final Duration EXPIRE = Duration.ofSeconds(10);

    /** 쿠폰 수보다 넉넉하게. 발급 중인 쿠폰은 많아야 수십 개다. */
    private static final int MAX_SIZE = 1_000;

    private final LoadingCache<UUID, Coupon> cache;

    @Autowired
    CachedIssuableCouponAdapter(CouponRepository coupons) {
        this(
                id -> coupons.findById(id).orElse(null),
                Ticker.systemTicker(),
                ForkJoinPool.commonPool());
    }

    CachedIssuableCouponAdapter(
            Function<UUID, @Nullable Coupon> loader, Ticker ticker, Executor executor) {
        this.cache =
                Caffeine.newBuilder()
                        .refreshAfterWrite(REFRESH)
                        .expireAfterWrite(EXPIRE)
                        .maximumSize(MAX_SIZE)
                        .ticker(ticker)
                        .executor(executor)
                        .build(loader::apply);
    }

    @Override
    public Optional<Coupon> findById(UUID couponId) {
        return Optional.ofNullable(cache.get(couponId));
    }
}
