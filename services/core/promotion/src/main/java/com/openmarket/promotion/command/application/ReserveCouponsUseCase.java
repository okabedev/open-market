package com.openmarket.promotion.command.application;

import com.openmarket.common.messaging.outbox.Outbox;
import com.openmarket.promotion.command.domain.CouponReservationFailed;
import com.openmarket.promotion.command.domain.CouponReserved;
import com.openmarket.promotion.command.domain.IssuedCoupon;
import com.openmarket.promotion.command.domain.IssuedCouponRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문 쿠폰을 모두 묶거나 하나도 묶지 않는다({@code OrderPlaced}, docs/architecture/promotion.md "주문 쿠폰 예약·사용·복원").
 * 발급 쿠폰 행을 ID 순서로 잠그고, 모두 구매자 것이고 쓸 수 있고 만료 전이면 묶는다. 결과는 {@code CouponReserved} 또는 {@code
 * CouponReservationFailed}로 낸다. 할인은 다시 계산하지 않는다(정책은 바뀌지 않는다).
 */
@Service
public class ReserveCouponsUseCase {

    public static final String TOPIC = "promotion.issued-coupon";

    private final IssuedCouponRepository issuedCoupons;
    private final Outbox outbox;
    private final Clock clock;

    public ReserveCouponsUseCase(IssuedCouponRepository issuedCoupons, Outbox outbox, Clock clock) {
        this.issuedCoupons = issuedCoupons;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public void execute(ReserveCouponsCommand command) {
        // 쿠폰이 없는 주문은 order가 쿠폰 결과를 기다리지 않는다.
        if (command.issuedCouponIds().isEmpty()) {
            return;
        }
        List<UUID> ids = new ArrayList<>(new LinkedHashSet<>(command.issuedCouponIds()));
        Map<UUID, IssuedCoupon> locked =
                issuedCoupons.findForUpdateByIdInOrderById(ids).stream()
                        .collect(Collectors.toMap(IssuedCoupon::id, Function.identity()));
        // 이미 이 주문에 모두 묶였거나 쓰였으면 처리한 주문이다(다른 이벤트 ID로 다시 옴).
        if (locked.size() == ids.size()
                && locked.values().stream().allMatch(c -> c.isBoundTo(command.orderId()))) {
            return;
        }
        Instant now = clock.instant();
        List<UUID> unavailable =
                ids.stream()
                        .filter(
                                id -> {
                                    IssuedCoupon coupon = locked.get(id);
                                    return coupon == null
                                            || (!coupon.isBoundTo(command.orderId())
                                                    && coupon.unavailableReason(
                                                                    command.buyerId(), now)
                                                            != null);
                                })
                        .toList();
        String key = command.orderId().toString();
        if (!unavailable.isEmpty()) {
            outbox.append(
                    TOPIC,
                    key,
                    CouponReservationFailed.TYPE,
                    new CouponReservationFailed(
                            command.orderId(),
                            CouponReservationFailed.COUPON_UNAVAILABLE,
                            unavailable));
            return;
        }
        locked.values().stream()
                .filter(coupon -> !coupon.isBoundTo(command.orderId()))
                .forEach(
                        coupon -> {
                            coupon.reserve(command.orderId(), now);
                            issuedCoupons.save(coupon);
                        });
        outbox.append(TOPIC, key, CouponReserved.TYPE, new CouponReserved(command.orderId(), ids));
    }
}
