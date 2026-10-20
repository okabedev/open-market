package com.openmarket.order.command.application;

import com.openmarket.common.messaging.outbox.Outbox;
import com.openmarket.order.command.domain.Order;
import com.openmarket.order.command.domain.OrderRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** 주문 유스케이스 테스트용 가짜 구현. */
final class OrderFakes {

    private OrderFakes() {}

    static final class Orders implements OrderRepository {
        final Map<UUID, Order> rows = new LinkedHashMap<>();

        @Override
        public Order save(Order order) {
            rows.put(order.id(), order);
            return order;
        }

        @Override
        public Optional<Order> findForUpdateById(UUID id) {
            return Optional.ofNullable(rows.get(id));
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
