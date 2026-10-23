package com.openmarket.promotion.command.domain;

import com.openmarket.promotion.command.domain.CouponDiscount.SellerOrderAmount;
import com.openmarket.promotion.command.domain.CouponDiscount.SellerOrderDiscount;
import com.openmarket.promotion.command.domain.CouponDiscount.Unavailable;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 주문의 쿠폰 할인 계산(docs/architecture/promotion.md "할인 계산"). order의 주문 생성이 내부 API로 부르고, 결과를 주문에 스냅샷으로
 * 저장한다. 정책은 만든 뒤 바뀌지 않으므로 같은 입력이면 같은 결과다.
 *
 * <ol>
 *   <li>판매자 쿠폰: 하위 주문마다 그 판매자 쿠폰 하나. 대상 금액은 하위 주문의 상품 금액.
 *   <li>플랫폼 쿠폰: 주문에 하나. 대상 금액은 판매자 쿠폰 할인을 뺀 주문 상품 금액.
 *   <li>플랫폼 할인은 (판매자 쿠폰을 뺀 하위 주문 금액) 비율로 내림해 나누고, 남은 원은 버린 값이 큰 하위 주문부터 1원씩 더한다(같으면 하위 주문 순서).
 * </ol>
 */
public final class DiscountCalculator {

    private DiscountCalculator() {}

    /**
     * 주문의 쿠폰 할인을 계산한다.
     *
     * @param issuedCouponIds 요청한 발급 쿠폰(요청 순서)
     * @param issued 요청한 발급 쿠폰 중 DB에 있는 것
     * @param coupons 그 발급 쿠폰들의 정책
     * @param sellerOrders 하위 주문. 판매자는 겹치지 않는다
     */
    public static CouponDiscount calculate(
            UUID buyerId,
            Instant now,
            List<UUID> issuedCouponIds,
            Map<UUID, IssuedCoupon> issued,
            Map<UUID, Coupon> coupons,
            List<SellerOrderAmount> sellerOrders) {
        Set<UUID> sellers = new HashSet<>();
        sellerOrders.forEach(
                order -> {
                    if (!sellers.add(order.sellerId()) || order.amount() < 0) {
                        throw new IllegalArgumentException("하위 주문의 판매자가 겹치거나 금액이 음수다");
                    }
                });
        Map<UUID, CouponUnavailableReason> unavailable = new LinkedHashMap<>();
        List<UUID> platformCandidates = new ArrayList<>();
        Map<UUID, List<UUID>> sellerCandidates = new HashMap<>();
        Set<UUID> seen = new HashSet<>();
        for (UUID id : issuedCouponIds) {
            if (!seen.add(id)) {
                unavailable.put(id, CouponUnavailableReason.DUPLICATE_SLOT);
                continue;
            }
            IssuedCoupon issuedCoupon = issued.get(id);
            Coupon coupon = issuedCoupon == null ? null : coupons.get(issuedCoupon.couponId());
            if (issuedCoupon == null || coupon == null) {
                unavailable.put(id, CouponUnavailableReason.NOT_OWNED);
                continue;
            }
            CouponUnavailableReason reason = issuedCoupon.unavailableReason(buyerId, now);
            UUID sellerId = coupon.sellerId();
            if (reason != null) {
                unavailable.put(id, reason);
            } else if (sellerId == null) {
                platformCandidates.add(id);
            } else if (!sellers.contains(sellerId)) {
                unavailable.put(id, CouponUnavailableReason.SELLER_NOT_IN_ORDER);
            } else {
                sellerCandidates.computeIfAbsent(sellerId, s -> new ArrayList<>()).add(id);
            }
        }
        UUID platformId = single(platformCandidates, unavailable);

        List<long[]> amounts = new ArrayList<>();
        List<@Nullable UUID> sellerCouponIds = new ArrayList<>();
        for (SellerOrderAmount order : sellerOrders) {
            UUID sellerCouponId =
                    single(sellerCandidates.getOrDefault(order.sellerId(), List.of()), unavailable);
            long sellerDiscount = 0;
            if (sellerCouponId != null) {
                Coupon coupon = couponOf(sellerCouponId, issued, coupons);
                if (coupon.appliesTo(order.amount())) {
                    sellerDiscount = coupon.discountFor(order.amount());
                } else {
                    unavailable.put(
                            sellerCouponId, CouponUnavailableReason.MIN_ORDER_AMOUNT_NOT_MET);
                    sellerCouponId = null;
                }
            }
            sellerCouponIds.add(sellerCouponId);
            amounts.add(new long[] {order.amount(), sellerDiscount});
        }

        long[] platformShares = new long[sellerOrders.size()];
        long platformDiscount = 0;
        if (platformId != null) {
            long[] bases = amounts.stream().mapToLong(a -> a[0] - a[1]).toArray();
            long target = sum(bases);
            Coupon coupon = couponOf(platformId, issued, coupons);
            if (coupon.appliesTo(target)) {
                platformDiscount = coupon.discountFor(target);
                platformShares = allocate(platformDiscount, bases);
            } else {
                unavailable.put(platformId, CouponUnavailableReason.MIN_ORDER_AMOUNT_NOT_MET);
                platformId = null;
            }
        }

        List<SellerOrderDiscount> discounts = new ArrayList<>();
        for (int i = 0; i < sellerOrders.size(); i++) {
            discounts.add(
                    new SellerOrderDiscount(
                            sellerOrders.get(i).sellerId(),
                            amounts.get(i)[0],
                            sellerCouponIds.get(i),
                            amounts.get(i)[1],
                            platformShares[i]));
        }
        List<Unavailable> reasons = new ArrayList<>();
        // 요청 순서로 돌려준다.
        for (UUID id : new LinkedHashSet<>(issuedCouponIds)) {
            CouponUnavailableReason reason = unavailable.get(id);
            if (reason != null) {
                reasons.add(new Unavailable(id, reason));
            }
        }
        return new CouponDiscount(discounts, platformId, platformDiscount, reasons);
    }

    /** 자리에 한 장이면 그 쿠폰. 두 장 이상이면 모두 같은 자리에 두 장이고 아무것도 쓰지 않는다. */
    private static @Nullable UUID single(
            List<UUID> candidates, Map<UUID, CouponUnavailableReason> unavailable) {
        if (candidates.size() > 1) {
            candidates.forEach(id -> unavailable.put(id, CouponUnavailableReason.DUPLICATE_SLOT));
            return null;
        }
        return candidates.isEmpty() ? null : candidates.getFirst();
    }

    /** {@code discount}를 {@code bases} 비율로 나눈다. 몫의 합은 {@code discount}이고 몫은 base를 넘지 않는다. */
    static long[] allocate(long discount, long[] bases) {
        long[] shares = new long[bases.length];
        long total = sum(bases);
        if (total == 0 || discount == 0) {
            return shares;
        }
        BigInteger bigTotal = BigInteger.valueOf(total);
        long[] remainders = new long[bases.length];
        long allocated = 0;
        for (int i = 0; i < bases.length; i++) {
            BigInteger[] division =
                    BigInteger.valueOf(discount)
                            .multiply(BigInteger.valueOf(bases[i]))
                            .divideAndRemainder(bigTotal);
            shares[i] = division[0].longValueExact();
            remainders[i] = division[1].longValueExact();
            allocated += shares[i];
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < bases.length; i++) {
            order.add(i);
        }
        order.sort(
                Comparator.<Integer>comparingLong(i -> remainders[i])
                        .reversed()
                        .thenComparingInt(i -> i));
        for (int k = 0; allocated < discount; k++) {
            shares[order.get(k)]++;
            allocated++;
        }
        return shares;
    }

    private static Coupon couponOf(
            UUID issuedCouponId, Map<UUID, IssuedCoupon> issued, Map<UUID, Coupon> coupons) {
        IssuedCoupon issuedCoupon = Objects.requireNonNull(issued.get(issuedCouponId));
        return Objects.requireNonNull(coupons.get(issuedCoupon.couponId()));
    }

    private static long sum(long[] values) {
        long total = 0;
        for (long value : values) {
            total = Math.addExact(total, value);
        }
        return total;
    }
}
