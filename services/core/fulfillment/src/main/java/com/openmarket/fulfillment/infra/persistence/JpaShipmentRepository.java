package com.openmarket.fulfillment.infra.persistence;

import com.openmarket.fulfillment.command.domain.Shipment;
import com.openmarket.fulfillment.command.domain.ShipmentRepository;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** {@link ShipmentRepository}의 Spring Data JPA 구현. */
public interface JpaShipmentRepository extends Repository<Shipment, UUID>, ShipmentRepository {

    @Override
    @Query(
            "SELECT count(s) > 0 FROM Shipment s WHERE s.sellerOrderId = :sellerOrderId AND"
                    + " s.claimId IS NULL")
    boolean existsFirstBySellerOrderId(UUID sellerOrderId);

    /** {@code SELECT ... FOR UPDATE}. 판매자 동작, 시뮬레이터, 구매자 취소가 겹쳐도 한 번에 하나만 바꾼다. */
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Shipment> findForUpdateById(UUID id);

    @Override
    @Query(
            value =
                    "SELECT * FROM shipment WHERE seller_order_id = :sellerOrderId ORDER BY"
                            + " created_at DESC, id DESC LIMIT 1 FOR UPDATE",
            nativeQuery = true)
    Optional<Shipment> findLatestForUpdateBySellerOrderId(UUID sellerOrderId);

    @Override
    @Query(
            value =
                    "SELECT id FROM shipment WHERE status = 'SHIPPED' AND shipped_at <="
                            + " :shippedBefore ORDER BY shipped_at LIMIT :limit",
            nativeQuery = true)
    List<UUID> findShippedIdsBefore(Instant shippedBefore, int limit);

    @Override
    @Query(
            value =
                    "SELECT s.id FROM shipment s WHERE s.status = 'DELIVERED' AND s.confirm_due_at"
                            + " <= :now AND NOT EXISTS (SELECT 1 FROM purchase_confirmation c WHERE"
                            + " c.seller_order_id = s.seller_order_id) AND NOT EXISTS (SELECT 1 FROM"
                            + " claim m WHERE m.seller_order_id = s.seller_order_id AND m.status IN"
                            + " ('REQUESTED', 'APPROVED', 'MEDIATING')) ORDER BY s.confirm_due_at"
                            + " LIMIT :limit",
            nativeQuery = true)
    List<UUID> findConfirmDueIds(Instant now, int limit);

    @Override
    @Query(
            value =
                    "SELECT id FROM shipment WHERE status = 'IN_TRANSIT' AND in_transit_at <="
                            + " :inTransitBefore ORDER BY in_transit_at LIMIT :limit",
            nativeQuery = true)
    List<UUID> findInTransitIdsBefore(Instant inTransitBefore, int limit);
}
