package com.openmarket.fulfillment.query.dao;

import static com.openmarket.fulfillment.command.domain.QShipment.shipment;

import com.openmarket.fulfillment.query.dto.SellerFulfillmentView;
import com.openmarket.fulfillment.query.dto.ShipmentStatus;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** 배송 조회. 하위 주문의 배송은 만든 순서로 읽는다(shipment_seller_order_idx). */
@Repository
public class FulfillmentQueryDao {

    private final JPAQueryFactory query;

    public FulfillmentQueryDao(JPAQueryFactory query) {
        this.query = query;
    }

    /** 판매자 본인 하위 주문의 배송 진행. 배송이 아직 없거나 다른 판매자의 것이면 비어 있다. */
    public Optional<SellerFulfillmentView> findForSeller(UUID sellerId, UUID sellerOrderId) {
        List<Tuple> rows =
                query.select(
                                shipment.id,
                                shipment.orderId,
                                shipment.status.stringValue(),
                                shipment.createdAt)
                        .from(shipment)
                        .where(
                                shipment.sellerOrderId.eq(sellerOrderId),
                                shipment.sellerId.eq(sellerId))
                        .orderBy(shipment.createdAt.asc(), shipment.id.asc())
                        .fetch();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        List<SellerFulfillmentView.Shipment> shipments =
                rows.stream()
                        .map(
                                row ->
                                        new SellerFulfillmentView.Shipment(
                                                Objects.requireNonNull(row.get(shipment.id)),
                                                status(row),
                                                Objects.requireNonNull(
                                                        row.get(shipment.createdAt))))
                        .toList();
        return Optional.of(
                new SellerFulfillmentView(
                        sellerOrderId,
                        Objects.requireNonNull(rows.getFirst().get(shipment.orderId)),
                        shipments.getLast().status(),
                        shipments));
    }

    private static ShipmentStatus status(Tuple row) {
        return ShipmentStatus.valueOf(Objects.requireNonNull(row.get(2, String.class)));
    }
}
