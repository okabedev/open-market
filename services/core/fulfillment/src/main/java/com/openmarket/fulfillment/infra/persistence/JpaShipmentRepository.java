package com.openmarket.fulfillment.infra.persistence;

import com.openmarket.fulfillment.command.domain.Shipment;
import com.openmarket.fulfillment.command.domain.ShipmentRepository;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** {@link ShipmentRepository}의 Spring Data JPA 구현. */
public interface JpaShipmentRepository extends Repository<Shipment, UUID>, ShipmentRepository {

    @Override
    @Query(
            "SELECT count(s) > 0 FROM Shipment s WHERE s.sellerOrderId = :sellerOrderId AND"
                    + " s.claimId IS NULL")
    boolean existsFirstBySellerOrderId(UUID sellerOrderId);
}
