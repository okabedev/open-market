package com.openmarket.order.command.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.order.command.application.CouponDiscountPort.CouponDiscount;
import com.openmarket.order.command.application.CouponDiscountPort.SellerAmount;
import com.openmarket.order.command.application.CouponDiscountPort.SellerOrderCoupon;
import com.openmarket.order.command.application.SkuCatalogPort.CatalogSku;
import com.openmarket.order.command.domain.CouponNotApplicableException;
import com.openmarket.order.command.domain.Order;
import com.openmarket.order.command.domain.OrderPlaced;
import com.openmarket.order.command.domain.OrderStatus;
import com.openmarket.order.command.domain.PriceChangedException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

class PlaceOrderUseCaseTest {

    static final Instant NOW = Instant.parse("2026-10-20T03:00:00Z");

    final OrderFakes.Orders orders = new OrderFakes.Orders();
    final OrderFakes.RecordingOutbox outbox = new OrderFakes.RecordingOutbox();
    final Map<UUID, CatalogSku> catalog = new HashMap<>();
    final List<Collection<UUID>> catalogCalls = new ArrayList<>();
    final SkuCatalogPort catalogPort =
            ids -> {
                catalogCalls.add(ids);
                return ids.stream().filter(catalog::containsKey).map(catalog::get).toList();
            };

    final List<List<SellerAmount>> promotionCalls = new ArrayList<>();
    @Nullable CouponDiscount discount;
    final CouponDiscountPort promotionPort =
            (buyer, couponIds, sellerOrders) -> {
                promotionCalls.add(sellerOrders);
                if (discount == null) {
                    throw new IllegalStateException("할인 결과를 두지 않았다");
                }
                return discount;
            };

    final PlaceOrderUseCase place =
            new PlaceOrderUseCase(
                    orders,
                    catalogPort,
                    promotionPort,
                    outbox,
                    TransactionOperations.withoutTransaction(),
                    Clock.fixed(NOW, ZoneOffset.UTC));

    final UUID buyerId = UUID.randomUUID();
    final UUID sellerA = UUID.randomUUID();
    final UUID sellerB = UUID.randomUUID();
    final UUID shirt = UUID.randomUUID();
    final UUID pants = UUID.randomUUID();

    PlaceOrderUseCaseTest() {
        catalog.put(shirt, CatalogSkus.sku(shirt, sellerA, "길동상회", true, 19_900, 5));
        catalog.put(pants, CatalogSkus.sku(pants, sellerB, "춘향상점", true, 39_900, 1));
    }

    @Test
    @DisplayName("확인을 지나면 PENDING 주문을 저장하고 OrderPlaced를 orderId 키로 낸다")
    void places() {
        UUID orderId = place.execute(command(line(shirt, 2, 19_900), line(pants, 1, 39_900)));

        Order order = orders.rows.get(orderId);
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.buyerId()).isEqualTo(buyerId);
        assertThat(order.amount()).isEqualTo(19_900 * 2 + 39_900);
        assertThat(order.sellerOrders()).hasSize(2);
        assertThat(order.shippingAddress().recipientName()).isEqualTo("홍길동");
        assertThat(outbox.events)
                .singleElement()
                .satisfies(
                        e -> {
                            assertThat(e.aggregateType()).isEqualTo("order.order");
                            assertThat(e.aggregateId()).isEqualTo(orderId.toString());
                            OrderPlaced placed = (OrderPlaced) e.payload();
                            assertThat(placed.lines())
                                    .containsExactly(
                                            new OrderPlaced.Line(shirt, 2),
                                            new OrderPlaced.Line(pants, 1));
                            assertThat(placed.issuedCouponIds()).isEmpty();
                        });
        assertThat(promotionCalls).isEmpty();
    }

    @Test
    @DisplayName("쿠폰이 있으면 하위 주문 금액으로 할인을 계산해 스냅샷으로 남기고, 주문 금액은 결제 금액이고 OrderPlaced에 쓴 쿠폰을 싣는다")
    void placesWithCoupons() {
        UUID platformCoupon = UUID.randomUUID();
        UUID sellerCoupon = UUID.randomUUID();
        discount =
                new CouponDiscount(
                        true,
                        List.of(
                                new SellerOrderCoupon(sellerA, 39_800, null, 0, 2_000, 37_800),
                                new SellerOrderCoupon(
                                        sellerB, 39_900, sellerCoupon, 5_000, 1_000, 33_900)),
                        platformCoupon,
                        3_000,
                        71_700,
                        List.of());

        UUID orderId =
                place.execute(
                        command(
                                List.of(sellerCoupon, platformCoupon),
                                71_700L,
                                line(shirt, 2, 19_900),
                                line(pants, 1, 39_900)));

        assertThat(promotionCalls)
                .containsExactly(
                        List.of(
                                new SellerAmount(sellerA, 39_800),
                                new SellerAmount(sellerB, 39_900)));
        Order order = orders.rows.get(orderId);
        assertThat(order.amount()).isEqualTo(71_700);
        assertThat(order.sellerOrders())
                .extracting(s -> s.paymentAmount())
                .containsExactly(37_800L, 33_900L);
        assertThat(order.sellerOrders().get(1).sellerIssuedCouponId()).isEqualTo(sellerCoupon);
        OrderPlaced placed = (OrderPlaced) outbox.events.getFirst().payload();
        assertThat(placed.issuedCouponIds()).containsExactly(platformCoupon, sellerCoupon);
    }

    @Test
    @DisplayName("쓸 수 없는 쿠폰이 있으면 주문을 만들지 않고 422 ORDER_COUPON_NOT_APPLICABLE에 쿠폰마다 이유를 담는다")
    void couponNotApplicable() {
        UUID coupon = UUID.randomUUID();
        discount =
                new CouponDiscount(
                        false,
                        List.of(new SellerOrderCoupon(sellerA, 19_900, null, 0, 0, 19_900)),
                        null,
                        0,
                        19_900,
                        List.of(new CouponDiscountPort.Unavailable(coupon, "EXPIRED")));

        assertThatThrownBy(
                        () -> place.execute(command(List.of(coupon), null, line(shirt, 1, 19_900))))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> {
                            assertThat(e.getCode()).isEqualTo("ORDER_COUPON_NOT_APPLICABLE");
                            assertThat(e.getProperties().get("coupons"))
                                    .isEqualTo(
                                            List.of(
                                                    new CouponNotApplicableException.Coupon(
                                                            coupon, "EXPIRED")));
                        });
        assertThat(orders.rows).isEmpty();
        assertThat(outbox.events).isEmpty();
    }

    @Test
    @DisplayName("화면이 보여 준 결제 금액과 다르면 주문을 만들지 않고 409 ORDER_AMOUNT_CHANGED다. 쿠폰이 없어도 비교한다")
    void amountChanged() {
        UUID coupon = UUID.randomUUID();
        discount =
                new CouponDiscount(
                        true,
                        List.of(new SellerOrderCoupon(sellerA, 19_900, coupon, 1_000, 0, 18_900)),
                        null,
                        0,
                        18_900,
                        List.of());

        assertThatThrownBy(
                        () ->
                                place.execute(
                                        command(List.of(coupon), 19_900L, line(shirt, 1, 19_900))))
                .extracting("code")
                .isEqualTo("ORDER_AMOUNT_CHANGED");
        assertThatThrownBy(() -> place.execute(command(List.of(), 1L, line(shirt, 1, 19_900))))
                .extracting("code")
                .isEqualTo("ORDER_AMOUNT_CHANGED");
        assertThat(orders.rows).isEmpty();

        place.execute(command(List.of(), 19_900L, line(shirt, 1, 19_900)));
        assertThat(orders.rows).hasSize(1);
    }

    @Test
    @DisplayName("가격이 바뀌었으면 주문을 만들지 않고 409 ORDER_PRICE_CHANGED에 바뀐 줄을 담는다")
    void priceChanged() {
        assertThatThrownBy(
                        () ->
                                place.execute(
                                        command(line(shirt, 1, 18_900), line(pants, 1, 39_900))))
                .isInstanceOfSatisfying(
                        PriceChangedException.class,
                        e ->
                                assertThat(e.getProperties().get("changes"))
                                        .isEqualTo(
                                                List.of(
                                                        new PriceChangedException.Change(
                                                                shirt, 18_900, 19_900))));
        assertThat(orders.rows).isEmpty();
        assertThat(outbox.events).isEmpty();
    }

    @Test
    @DisplayName("없는 SKU는 404, 판매 중이 아니면 422, 수량이 모자라면 422이고 문제 SKU를 담는다")
    void catalogChecks() {
        UUID unknown = UUID.randomUUID();
        assertCode(command(line(unknown, 1, 1_000)), "ORDER_SKU_NOT_FOUND", unknown);

        catalog.put(shirt, CatalogSkus.sku(shirt, sellerA, "길동상회", false, 19_900, 5));
        assertCode(command(line(shirt, 1, 19_900)), "ORDER_SKU_NOT_ON_SALE", shirt);

        assertCode(command(line(pants, 2, 39_900)), "ORDER_INSUFFICIENT_STOCK", pants);
        assertThat(orders.rows).isEmpty();
    }

    @Test
    @DisplayName("입력 규칙(줄 1~50개, SKU 중복 없음, 수량 1~99, 기대 가격 0 이상, 배송지)은 catalog를 부르기 전에 400이다")
    void inputRules() {
        assertInvalid(command());
        assertInvalid(command(line(shirt, 1, 19_900), line(shirt, 1, 19_900)));
        assertInvalid(command(line(shirt, 0, 19_900)));
        assertInvalid(command(line(shirt, 100, 19_900)));
        assertInvalid(command(line(shirt, 1, -1)));
        assertInvalid(
                command(
                        IntStream.range(0, 51)
                                .mapToObj(i -> line(UUID.randomUUID(), 1, 1_000))
                                .toArray(PlaceOrderCommand.Line[]::new)));
        assertInvalid(
                new PlaceOrderCommand(
                        buyerId,
                        List.of(line(shirt, 1, 19_900)),
                        new PlaceOrderCommand.Shipping("홍길동", "010-1234-5678", "123", "주소", null),
                        List.of(),
                        null));
        UUID coupon = UUID.randomUUID();
        assertInvalid(command(List.of(coupon, coupon), null, line(shirt, 1, 19_900)));
        assertInvalid(
                command(
                        IntStream.range(0, 51).mapToObj(i -> UUID.randomUUID()).toList(),
                        null,
                        line(shirt, 1, 19_900)));
        assertInvalid(command(List.of(), -1L, line(shirt, 1, 19_900)));
        assertThat(catalogCalls).isEmpty();
    }

    private void assertCode(PlaceOrderCommand command, String code, UUID skuId) {
        assertThatThrownBy(() -> place.execute(command))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> {
                            assertThat(e.getCode()).isEqualTo(code);
                            assertThat(e.getProperties().get("skuIds")).isEqualTo(List.of(skuId));
                        });
    }

    private void assertInvalid(PlaceOrderCommand command) {
        assertThatThrownBy(() -> place.execute(command))
                .extracting("code")
                .isEqualTo("ORDER_INVALID_INPUT");
    }

    private PlaceOrderCommand command(PlaceOrderCommand.Line... lines) {
        return command(List.of(), null, lines);
    }

    private PlaceOrderCommand command(
            List<UUID> issuedCouponIds,
            @Nullable Long expectedAmount,
            PlaceOrderCommand.Line... lines) {
        return new PlaceOrderCommand(
                buyerId,
                List.of(lines),
                new PlaceOrderCommand.Shipping(
                        "홍길동", "010-1234-5678", "06236", "서울 강남구 테헤란로 1", "101호"),
                issuedCouponIds,
                expectedAmount);
    }

    private static PlaceOrderCommand.Line line(UUID skuId, int quantity, long expectedPrice) {
        return new PlaceOrderCommand.Line(skuId, quantity, expectedPrice);
    }
}
