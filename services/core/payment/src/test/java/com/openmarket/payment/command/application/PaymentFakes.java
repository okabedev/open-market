package com.openmarket.payment.command.application;

import com.openmarket.common.messaging.outbox.Outbox;
import com.openmarket.payment.command.domain.Payment;
import com.openmarket.payment.command.domain.PaymentLedgerEntry;
import com.openmarket.payment.command.domain.PaymentLedgerRepository;
import com.openmarket.payment.command.domain.PaymentRepository;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** 결제 유스케이스 테스트용 가짜 구현. */
final class PaymentFakes {

    private PaymentFakes() {}

    static final class Payments implements PaymentRepository {
        final Map<UUID, Payment> rows = new LinkedHashMap<>();

        @Override
        public Payment save(Payment payment) {
            rows.put(payment.id(), payment);
            return payment;
        }

        @Override
        public boolean existsByOrderId(UUID orderId) {
            return rows.values().stream().anyMatch(p -> p.orderId().equals(orderId));
        }

        @Override
        public Optional<Payment> findForUpdateById(UUID id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public Optional<Payment> findForUpdateByOrderId(UUID orderId) {
            return rows.values().stream().filter(p -> p.orderId().equals(orderId)).findFirst();
        }

        @Override
        public List<UUID> findInProgressIdsRequestedBefore(Instant requestedBefore, int limit) {
            throw new UnsupportedOperationException("통합 테스트에서 확인한다");
        }
    }

    static final class Ledger implements PaymentLedgerRepository {
        final List<PaymentLedgerEntry> entries = new ArrayList<>();

        @Override
        public PaymentLedgerEntry save(PaymentLedgerEntry entry) {
            entries.add(entry);
            return entry;
        }
    }

    /** 정해 둔 결과를 차례로 돌려주고 받은 요청을 남긴다. */
    static final class Gateway implements PaymentGatewayPort {
        final Deque<Result> confirmResults = new ArrayDeque<>();
        final Deque<Result> findResults = new ArrayDeque<>();
        final List<String> calls = new ArrayList<>();

        @Override
        public Result confirm(String paymentKey, UUID orderId, long amount, String idempotencyKey) {
            calls.add("confirm " + paymentKey + " " + amount + " " + idempotencyKey);
            return confirmResults.removeFirst();
        }

        @Override
        public Result find(String paymentKey) {
            calls.add("find " + paymentKey);
            return findResults.removeFirst();
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
