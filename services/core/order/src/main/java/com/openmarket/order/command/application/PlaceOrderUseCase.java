package com.openmarket.order.command.application;

import com.openmarket.common.messaging.outbox.Outbox;
import com.openmarket.order.command.application.CouponDiscountPort.CouponDiscount;
import com.openmarket.order.command.application.SkuCatalogPort.CatalogSku;
import com.openmarket.order.command.domain.AmountChangedException;
import com.openmarket.order.command.domain.CartItem;
import com.openmarket.order.command.domain.CouponNotApplicableException;
import com.openmarket.order.command.domain.InsufficientStockException;
import com.openmarket.order.command.domain.InvalidOrderInputException;
import com.openmarket.order.command.domain.Order;
import com.openmarket.order.command.domain.OrderItem;
import com.openmarket.order.command.domain.OrderPlaced;
import com.openmarket.order.command.domain.OrderRepository;
import com.openmarket.order.command.domain.PriceChangedException;
import com.openmarket.order.command.domain.SellerOrderDiscount;
import com.openmarket.order.command.domain.ShippingAddress;
import com.openmarket.order.command.domain.SkuNotFoundException;
import com.openmarket.order.command.domain.SkuNotOnSaleException;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * 주문을 만든다(ORD-2, docs/architecture/order.md "주문 생성", "쿠폰 적용"). catalog에서 판매 상태, 가격, 수량을 다시 확인하고,
 * 쿠폰이 있으면 promotion에서 할인을 계산한다. 하나라도 걸리면 주문을 만들지 않는다. 주문은 {@code PENDING}으로 저장하고 {@code
 * OrderPlaced}를 낸다. catalog·promotion 조회는 트랜잭션 밖에서 한다.
 */
@Service
public class PlaceOrderUseCase {

    static final String TOPIC = "order.order";

    /** 주문 하나의 줄 수 상한. 장바구니 상한과 같다. */
    static final int MAX_LINES = 50;

    /** 주문 하나의 쿠폰 수 상한. promotion 할인 계산 API의 상한과 같다. */
    static final int MAX_COUPONS = 50;

    private final OrderRepository orders;
    private final SkuCatalogPort catalog;
    private final CouponDiscountPort promotion;
    private final Outbox outbox;
    private final TransactionOperations transaction;
    private final Clock clock;

    public PlaceOrderUseCase(
            OrderRepository orders,
            SkuCatalogPort catalog,
            CouponDiscountPort promotion,
            Outbox outbox,
            TransactionOperations transaction,
            Clock clock) {
        this.orders = orders;
        this.catalog = catalog;
        this.promotion = promotion;
        this.outbox = outbox;
        this.transaction = transaction;
        this.clock = clock;
    }

    /** 만든 주문의 ID. */
    public UUID execute(PlaceOrderCommand command) {
        ShippingAddress address = checkInput(command);
        List<UUID> skuIds = command.lines().stream().map(PlaceOrderCommand.Line::skuId).toList();
        Map<UUID, CatalogSku> skus =
                catalog.findAll(skuIds).stream()
                        .collect(
                                Collectors.toMap(
                                        CatalogSku::skuId, Function.identity(), (a, b) -> a));
        checkCatalog(command.lines(), skus);
        Order order =
                Order.place(
                        command.buyerId(),
                        command.lines().stream()
                                .map(
                                        line ->
                                                item(
                                                        Objects.requireNonNull(
                                                                skus.get(line.skuId())),
                                                        line.quantity()))
                                .toList(),
                        address,
                        clock.instant());
        if (!command.issuedCouponIds().isEmpty()) {
            applyCoupons(order, command.issuedCouponIds());
        }
        Long expectedAmount = command.expectedAmount();
        if (expectedAmount != null && expectedAmount != order.amount()) {
            throw new AmountChangedException(expectedAmount, order.amount());
        }
        transaction.executeWithoutResult(
                status -> {
                    orders.save(order);
                    outbox.append(
                            TOPIC, order.id().toString(), OrderPlaced.TYPE, OrderPlaced.of(order));
                });
        return order.id();
    }

    /** promotion이 계산한 할인을 주문에 기록한다. 쓸 수 없는 쿠폰이 있으면 422다. */
    private void applyCoupons(Order order, List<UUID> issuedCouponIds) {
        CouponDiscount discount =
                promotion.calculate(
                        order.buyerId(),
                        issuedCouponIds,
                        order.sellerOrders().stream()
                                .map(
                                        sellerOrder ->
                                                new CouponDiscountPort.SellerAmount(
                                                        sellerOrder.sellerId(),
                                                        sellerOrder.amount()))
                                .toList());
        if (!discount.applicable()) {
            throw new CouponNotApplicableException(
                    discount.unavailable().stream()
                            .map(
                                    unavailable ->
                                            new CouponNotApplicableException.Coupon(
                                                    unavailable.issuedCouponId(),
                                                    unavailable.reason()))
                            .toList());
        }
        order.applyCoupons(
                discount.platformIssuedCouponId(),
                discount.sellerOrders().stream()
                        .map(
                                sellerOrder ->
                                        new SellerOrderDiscount(
                                                sellerOrder.sellerId(),
                                                sellerOrder.sellerIssuedCouponId(),
                                                sellerOrder.sellerCouponDiscount(),
                                                sellerOrder.platformCouponDiscount()))
                        .toList());
        if (order.amount() != discount.paymentAmount()) {
            throw new IllegalStateException("promotion의 결제 금액이 하위 주문 결제 금액의 합과 다르다");
        }
    }

    private static ShippingAddress checkInput(PlaceOrderCommand command) {
        List<PlaceOrderCommand.Line> lines = command.lines();
        if (lines.isEmpty() || lines.size() > MAX_LINES) {
            throw new InvalidOrderInputException("주문할 상품은 1~50개입니다.");
        }
        if (new HashSet<>(lines.stream().map(PlaceOrderCommand.Line::skuId).toList()).size()
                != lines.size()) {
            throw new InvalidOrderInputException("같은 상품을 두 줄로 주문할 수 없습니다.");
        }
        for (PlaceOrderCommand.Line line : lines) {
            CartItem.checkQuantity(line.quantity());
            if (line.expectedPrice() < 0) {
                throw new InvalidOrderInputException("가격이 올바르지 않습니다.");
            }
        }
        List<UUID> couponIds = command.issuedCouponIds();
        if (couponIds.size() > MAX_COUPONS || new HashSet<>(couponIds).size() != couponIds.size()) {
            throw new InvalidOrderInputException("쿠폰은 50장까지, 같은 쿠폰은 한 번만 쓸 수 있습니다.");
        }
        Long expectedAmount = command.expectedAmount();
        if (expectedAmount != null && expectedAmount < 0) {
            throw new InvalidOrderInputException("결제 금액이 올바르지 않습니다.");
        }
        PlaceOrderCommand.Shipping shipping = command.shipping();
        return ShippingAddress.of(
                shipping.recipientName(),
                shipping.recipientPhone(),
                shipping.zipCode(),
                shipping.baseAddress(),
                shipping.detailAddress());
    }

    /** 없음 → 판매 중 아님 → 가격 변경 → 수량 순서로 확인한다. 먼저 걸린 종류의 SKU를 모두 알린다. */
    private static void checkCatalog(
            List<PlaceOrderCommand.Line> lines, Map<UUID, CatalogSku> skus) {
        List<UUID> missing =
                lines.stream()
                        .map(PlaceOrderCommand.Line::skuId)
                        .filter(id -> !skus.containsKey(id))
                        .toList();
        if (!missing.isEmpty()) {
            throw new SkuNotFoundException(missing);
        }
        List<UUID> notOnSale =
                lines.stream()
                        .map(PlaceOrderCommand.Line::skuId)
                        .filter(id -> !Objects.requireNonNull(skus.get(id)).onSale())
                        .toList();
        if (!notOnSale.isEmpty()) {
            throw new SkuNotOnSaleException(notOnSale);
        }
        List<PriceChangedException.Change> changes =
                lines.stream()
                        .filter(
                                line ->
                                        Objects.requireNonNull(skus.get(line.skuId())).price()
                                                != line.expectedPrice())
                        .map(
                                line ->
                                        new PriceChangedException.Change(
                                                line.skuId(),
                                                line.expectedPrice(),
                                                Objects.requireNonNull(skus.get(line.skuId()))
                                                        .price()))
                        .toList();
        if (!changes.isEmpty()) {
            throw new PriceChangedException(changes);
        }
        List<UUID> insufficient =
                lines.stream()
                        .filter(
                                line ->
                                        Objects.requireNonNull(skus.get(line.skuId()))
                                                        .availableQuantity()
                                                < line.quantity())
                        .map(PlaceOrderCommand.Line::skuId)
                        .toList();
        if (!insufficient.isEmpty()) {
            throw new InsufficientStockException(insufficient);
        }
    }

    private static OrderItem item(CatalogSku sku, int quantity) {
        return new OrderItem(
                sku.skuId(),
                sku.productId(),
                sku.productName(),
                sku.sellerId(),
                sku.storeName(),
                sku.categoryId(),
                sku.optionNames(),
                sku.optionValues(),
                sku.price(),
                quantity);
    }
}
