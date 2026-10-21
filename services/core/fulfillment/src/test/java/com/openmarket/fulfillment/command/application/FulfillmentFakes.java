package com.openmarket.fulfillment.command.application;

import com.openmarket.common.messaging.outbox.Outbox;
import com.openmarket.fulfillment.command.domain.PurchaseConfirmation;
import com.openmarket.fulfillment.command.domain.PurchaseConfirmationRepository;
import com.openmarket.fulfillment.command.domain.Shipment;
import com.openmarket.fulfillment.command.domain.ShipmentRepository;
import com.openmarket.fulfillment.command.domain.ShipmentStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** 유스케이스 단위 테스트의 가짜 구현. */
final class FulfillmentFakes {

    private FulfillmentFakes() {}

    static final class FakeShipmentRepository implements ShipmentRepository {
        final List<Shipment> shipments = new ArrayList<>();

        @Override
        public Shipment save(Shipment shipment) {
            if (!shipments.contains(shipment)) {
                shipments.add(shipment);
            }
            return shipment;
        }

        @Override
        public boolean existsFirstBySellerOrderId(UUID sellerOrderId) {
            return shipments.stream().anyMatch(s -> s.sellerOrderId().equals(sellerOrderId));
        }

        @Override
        public Optional<Shipment> findForUpdateById(UUID id) {
            return shipments.stream().filter(s -> s.id().equals(id)).findFirst();
        }

        @Override
        public Optional<Shipment> findLatestForUpdateBySellerOrderId(UUID sellerOrderId) {
            return shipments.stream()
                    .filter(s -> s.sellerOrderId().equals(sellerOrderId))
                    .reduce((first, second) -> second);
        }

        @Override
        public List<UUID> findShippedIdsBefore(Instant shippedBefore, int limit) {
            return due(ShipmentStatus.SHIPPED, Shipment::shippedAt, shippedBefore, limit);
        }

        @Override
        public List<UUID> findConfirmDueIds(Instant now, int limit) {
            throw new UnsupportedOperationException("저장소 통합 테스트에서 확인한다");
        }

        @Override
        public List<UUID> findInTransitIdsBefore(Instant inTransitBefore, int limit) {
            return due(ShipmentStatus.IN_TRANSIT, Shipment::inTransitAt, inTransitBefore, limit);
        }

        private List<UUID> due(
                ShipmentStatus status,
                java.util.function.Function<Shipment, Instant> at,
                Instant before,
                int limit) {
            return shipments.stream()
                    .filter(s -> s.status() == status)
                    .filter(s -> !Objects.requireNonNull(at.apply(s)).isAfter(before))
                    .sorted(Comparator.comparing(at))
                    .limit(limit)
                    .map(Shipment::id)
                    .toList();
        }
    }

    static final class FakePurchaseConfirmations implements PurchaseConfirmationRepository {
        final List<PurchaseConfirmation> confirmations = new ArrayList<>();

        @Override
        public PurchaseConfirmation save(PurchaseConfirmation confirmation) {
            if (existsBySellerOrderId(confirmation.sellerOrderId())) {
                throw new IllegalStateException("하위 주문당 한 번이다(유일 인덱스)");
            }
            confirmations.add(confirmation);
            return confirmation;
        }

        @Override
        public boolean existsBySellerOrderId(UUID sellerOrderId) {
            return confirmations.stream().anyMatch(c -> c.sellerOrderId().equals(sellerOrderId));
        }
    }

    static final class RecordingOutbox implements Outbox {
        final List<Appended> events = new ArrayList<>();

        @Override
        public UUID append(
                String aggregateType,
                String aggregateId,
                String type,
                int schemaVersion,
                Object payload) {
            events.add(new Appended(aggregateType, aggregateId, type, payload));
            return UUID.randomUUID();
        }

        List<String> types() {
            return events.stream().map(Appended::type).toList();
        }
    }

    record Appended(String aggregateType, String aggregateId, String type, Object payload) {}
}
