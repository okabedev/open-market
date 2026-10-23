package com.openmarket.promotion.command.application;

import com.openmarket.promotion.command.domain.Coupon;
import com.openmarket.promotion.command.domain.CouponDiscount;
import com.openmarket.promotion.command.domain.CouponDiscount.SellerOrderAmount;
import com.openmarket.promotion.command.domain.CouponRepository;
import com.openmarket.promotion.command.domain.DiscountCalculator;
import com.openmarket.promotion.command.domain.InvalidCouponException;
import com.openmarket.promotion.command.domain.IssuedCoupon;
import com.openmarket.promotion.command.domain.IssuedCouponRepository;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문의 쿠폰 할인을 계산한다(PRM-3, docs/architecture/promotion.md "할인 계산"). 상태를 바꾸지 않는다. 계산 규칙이 도메인 규칙({@link
 * DiscountCalculator})이라 커맨드 쪽에 둔다. order가 주문을 만들 때 내부 API로 부르고 결과를 스냅샷으로 저장한다. 쿠폰은 여기서 묶지 않고
 * {@code OrderPlaced}로 묶는다.
 */
@Service
public class CalculateCouponDiscountUseCase {

    /** 한 주문의 쿠폰 수 상한. 플랫폼 1장 + 판매자마다 1장인데, 하위 주문 수보다 넉넉하게 둔다. */
    static final int MAX_COUPONS = 50;

    private final IssuedCouponRepository issuedCoupons;
    private final CouponRepository coupons;
    private final Clock clock;

    public CalculateCouponDiscountUseCase(
            IssuedCouponRepository issuedCoupons, CouponRepository coupons, Clock clock) {
        this.issuedCoupons = issuedCoupons;
        this.coupons = coupons;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Result execute(CalculateCouponDiscountCommand command) {
        if (command.issuedCouponIds().size() > MAX_COUPONS) {
            throw new InvalidCouponException("한 주문에 쓸 수 있는 쿠폰 수를 넘었습니다.");
        }
        if (command.sellerOrders().isEmpty()
                || command.sellerOrders().stream().anyMatch(o -> o.amount() < 0)
                || command.sellerOrders().stream()
                                .map(CalculateCouponDiscountCommand.SellerOrder::sellerId)
                                .distinct()
                                .count()
                        != command.sellerOrders().size()) {
            throw new InvalidCouponException("하위 주문은 1개 이상이고 판매자가 겹치지 않으며 금액은 0원 이상입니다.");
        }
        Map<UUID, IssuedCoupon> issued =
                issuedCoupons.findAllByIdIn(new HashSet<>(command.issuedCouponIds())).stream()
                        .collect(Collectors.toMap(IssuedCoupon::id, Function.identity()));
        Map<UUID, Coupon> policies =
                coupons
                        .findAllByIdIn(
                                issued.values().stream()
                                        .map(IssuedCoupon::couponId)
                                        .collect(Collectors.toSet()))
                        .stream()
                        .collect(Collectors.toMap(Coupon::id, Function.identity()));
        CouponDiscount discount =
                DiscountCalculator.calculate(
                        command.buyerId(),
                        clock.instant(),
                        command.issuedCouponIds(),
                        issued,
                        policies,
                        command.sellerOrders().stream()
                                .map(o -> new SellerOrderAmount(o.sellerId(), o.amount()))
                                .toList());
        return new Result(
                discount.applicable(),
                discount.sellerOrders().stream()
                        .map(
                                o ->
                                        new Result.SellerOrder(
                                                o.sellerId(),
                                                o.amount(),
                                                o.sellerIssuedCouponId(),
                                                o.sellerCouponDiscount(),
                                                o.platformCouponDiscount(),
                                                o.paymentAmount()))
                        .toList(),
                discount.platformIssuedCouponId(),
                discount.platformDiscount(),
                discount.paymentAmount(),
                discount.unavailable().stream()
                        .map(u -> new Result.Unavailable(u.issuedCouponId(), u.reason().name()))
                        .toList());
    }

    /**
     * 계산 결과. {@code applicable}이 {@code false}면 {@code unavailable}에 쿠폰마다 이유가 있다.
     *
     * @param sellerOrders 요청한 하위 주문 순서
     * @param paymentAmount 주문 결제 금액(하위 주문 결제 금액의 합)
     */
    public record Result(
            boolean applicable,
            List<SellerOrder> sellerOrders,
            @Nullable UUID platformIssuedCouponId,
            long platformCouponDiscount,
            long paymentAmount,
            List<Unavailable> unavailable) {

        public record SellerOrder(
                UUID sellerId,
                long amount,
                @Nullable UUID sellerIssuedCouponId,
                long sellerCouponDiscount,
                long platformCouponDiscount,
                long paymentAmount) {}

        /**
         * 쓸 수 없는 쿠폰과 이유.
         *
         * @param reason {@code NOT_OWNED}, {@code NOT_AVAILABLE}, {@code EXPIRED}, {@code
         *     MIN_ORDER_AMOUNT_NOT_MET}, {@code SELLER_NOT_IN_ORDER}, {@code DUPLICATE_SLOT}
         */
        public record Unavailable(UUID issuedCouponId, String reason) {}
    }
}
