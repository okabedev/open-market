package com.openmarket.promotion.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmarket.promotion.command.domain.Coupon;
import com.openmarket.promotion.command.domain.CouponTerms;
import com.openmarket.promotion.command.domain.DiscountType;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CachedIssuableCouponAdapterTest {

    static final Instant NOW = Instant.parse("2026-10-27T00:00:00Z");

    final Map<UUID, Coupon> rows = new HashMap<>();
    final AtomicInteger reads = new AtomicInteger();
    final AtomicLong nanos = new AtomicLong();

    /** 뒤에서 새로 읽는 작업을 모아 두었다가 테스트가 돌린다. */
    final Queue<Runnable> background = new ArrayDeque<>();

    final CachedIssuableCouponAdapter adapter =
            new CachedIssuableCouponAdapter(
                    id -> {
                        reads.incrementAndGet();
                        return rows.get(id);
                    },
                    nanos::get,
                    background::add);

    @Test
    @DisplayName("같은 쿠폰은 새로 읽기 주기 안에 DB를 다시 읽지 않고, 지나면 지금 값을 주면서 새로 읽는다")
    void refreshesAfterInterval() {
        Coupon coupon = coupon();
        Coupon reloaded = coupon();
        rows.put(coupon.id(), coupon);

        assertThat(adapter.findById(coupon.id())).containsSame(coupon);
        assertThat(adapter.findById(coupon.id())).containsSame(coupon);
        assertThat(reads).hasValue(1);

        rows.put(coupon.id(), reloaded);
        nanos.addAndGet(CachedIssuableCouponAdapter.REFRESH.plusMillis(1).toNanos());
        // 새로 읽기를 뒤로 미루고 기다리지 않고 지금 값을 준다.
        assertThat(adapter.findById(coupon.id())).containsSame(coupon);
        assertThat(reads).hasValue(1);

        runBackground();
        assertThat(reads).hasValue(2);
        assertThat(adapter.findById(coupon.id())).containsSame(reloaded);
    }

    @Test
    @DisplayName("새로 읽지 못한 채 만료 시간이 지나면 DB를 다시 읽을 때까지 기다린다")
    void expiresWhenNotRefreshed() {
        Coupon coupon = coupon();
        rows.put(coupon.id(), coupon);
        adapter.findById(coupon.id());

        nanos.addAndGet(CachedIssuableCouponAdapter.EXPIRE.plusMillis(1).toNanos());
        rows.remove(coupon.id());

        assertThat(adapter.findById(coupon.id())).isEmpty();
        assertThat(reads).hasValue(2);
    }

    @Test
    @DisplayName("없는 쿠폰은 캐시하지 않아 곧 만든 쿠폰도 바로 찾는다")
    void doesNotCacheMissing() {
        Coupon coupon = coupon();

        assertThat(adapter.findById(coupon.id())).isEmpty();
        rows.put(coupon.id(), coupon);

        assertThat(adapter.findById(coupon.id())).containsSame(coupon);
        assertThat(reads).hasValue(2);
    }

    private void runBackground() {
        for (Runnable task = background.poll(); task != null; task = background.poll()) {
            task.run();
        }
    }

    private static Coupon coupon() {
        return Coupon.platform(
                new CouponTerms(
                        "선착순",
                        DiscountType.FIXED,
                        1_000,
                        null,
                        0,
                        100,
                        NOW.minusSeconds(60),
                        NOW.plus(Duration.ofHours(1)),
                        7),
                NOW.minusSeconds(120));
    }
}
