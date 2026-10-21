package com.openmarket.fulfillment.query.dao;

import static com.openmarket.fulfillment.command.domain.QPurchaseConfirmation.purchaseConfirmation;
import static com.openmarket.fulfillment.command.domain.QShipment.shipment;

import com.openmarket.fulfillment.query.dto.BuyerFulfillmentView;
import com.openmarket.fulfillment.query.dto.Carrier;
import com.openmarket.fulfillment.query.dto.ConfirmMode;
import com.openmarket.fulfillment.query.dto.SellerFulfillmentView;
import com.openmarket.fulfillment.query.dto.ShipmentStatus;
import com.openmarket.fulfillment.query.dto.ShipmentView;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.StringExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** 배송 조회. 하위 주문의 배송은 만든 순서로 읽는다(shipment_seller_order_idx). */
@Repository
public class FulfillmentQueryDao {

    private static final StringExpression STATUS = shipment.status.stringValue();
    private static final StringExpression CARRIER = shipment.trackingInfo.carrier.stringValue();
    private static final StringExpression MODE = purchaseConfirmation.mode.stringValue();

    private final JPAQueryFactory query;

    public FulfillmentQueryDao(JPAQueryFactory query) {
        this.query = query;
    }

    /** 판매자 본인 하위 주문의 배송 진행. 배송이 아직 없거나 다른 판매자의 것이면 비어 있다. */
    public Optional<SellerFulfillmentView> findForSeller(UUID sellerId, UUID sellerOrderId) {
        List<Tuple> rows = shipments(sellerOrderId, shipment.sellerId.eq(sellerId));
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        List<ShipmentView> views = rows.stream().map(FulfillmentQueryDao::toView).toList();
        return Optional.of(
                new SellerFulfillmentView(
                        sellerOrderId, orderId(rows), views.getLast().status(), views));
    }

    /** 구매자 본인 하위 주문의 배송과 구매확정. 배송이 아직 없거나 다른 구매자의 것이면 비어 있다. */
    public Optional<BuyerFulfillmentView> findForBuyer(UUID buyerId, UUID sellerOrderId) {
        List<Tuple> rows = shipments(sellerOrderId, shipment.buyerId.eq(buyerId));
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        List<ShipmentView> views = rows.stream().map(FulfillmentQueryDao::toView).toList();
        Tuple confirmation =
                query.select(purchaseConfirmation.confirmedAt, MODE)
                        .from(purchaseConfirmation)
                        .where(purchaseConfirmation.sellerOrderId.eq(sellerOrderId))
                        .fetchOne();
        String mode = confirmation == null ? null : confirmation.get(MODE);
        return Optional.of(
                new BuyerFulfillmentView(
                        sellerOrderId,
                        orderId(rows),
                        views.getLast().status(),
                        views,
                        rows.getLast().get(shipment.confirmDueAt),
                        confirmation == null
                                ? null
                                : confirmation.get(purchaseConfirmation.confirmedAt),
                        mode == null ? null : ConfirmMode.valueOf(mode)));
    }

    private List<Tuple> shipments(UUID sellerOrderId, Predicate owner) {
        return query.select(
                        shipment.id,
                        shipment.orderId,
                        STATUS,
                        CARRIER,
                        shipment.trackingInfo.trackingNumber,
                        shipment.createdAt,
                        shipment.acceptedAt,
                        shipment.shippedAt,
                        shipment.inTransitAt,
                        shipment.deliveredAt,
                        shipment.confirmDueAt)
                .from(shipment)
                .where(shipment.sellerOrderId.eq(sellerOrderId), owner)
                .orderBy(shipment.createdAt.asc(), shipment.id.asc())
                .fetch();
    }

    private static UUID orderId(List<Tuple> rows) {
        return Objects.requireNonNull(rows.getFirst().get(shipment.orderId));
    }

    private static ShipmentView toView(Tuple row) {
        String carrier = row.get(CARRIER);
        Instant createdAt = Objects.requireNonNull(row.get(shipment.createdAt));
        return new ShipmentView(
                Objects.requireNonNull(row.get(shipment.id)),
                ShipmentStatus.valueOf(Objects.requireNonNull(row.get(STATUS))),
                carrier == null ? null : Carrier.valueOf(carrier),
                row.get(shipment.trackingInfo.trackingNumber),
                createdAt,
                row.get(shipment.acceptedAt),
                row.get(shipment.shippedAt),
                row.get(shipment.inTransitAt),
                row.get(shipment.deliveredAt));
    }
}
