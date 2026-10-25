package com.openmarket.settlement.command.application;

import com.openmarket.common.core.audit.AuditEntry;
import com.openmarket.common.core.audit.AuditLog;
import com.openmarket.common.messaging.outbox.Outbox;
import com.openmarket.settlement.command.domain.CommissionRate;
import com.openmarket.settlement.command.domain.CommissionRateRepository;
import com.openmarket.settlement.command.domain.SellerLock;
import com.openmarket.settlement.command.domain.SellerPeriod;
import com.openmarket.settlement.command.domain.Settlement;
import com.openmarket.settlement.command.domain.SettlementItem;
import com.openmarket.settlement.command.domain.SettlementItemRepository;
import com.openmarket.settlement.command.domain.SettlementRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** settlement 유스케이스 테스트용 가짜 구현. */
final class SettlementFakes {

    private SettlementFakes() {}

    static final class Rates implements CommissionRateRepository {
        final Map<UUID, CommissionRate> rows = new LinkedHashMap<>();

        @Override
        public CommissionRate save(CommissionRate rate) {
            rows.put(rate.categoryId(), rate);
            return rate;
        }

        @Override
        public Optional<CommissionRate> findForUpdateById(UUID categoryId) {
            return Optional.ofNullable(rows.get(categoryId));
        }

        @Override
        public List<CommissionRate> findAllById(Iterable<UUID> categoryIds) {
            List<CommissionRate> found = new ArrayList<>();
            categoryIds.forEach(
                    id -> {
                        if (rows.containsKey(id)) {
                            found.add(rows.get(id));
                        }
                    });
            return found;
        }

        @Override
        public void delete(CommissionRate rate) {
            rows.remove(rate.categoryId());
        }
    }

    static final class Items implements SettlementItemRepository {
        final Map<UUID, SettlementItem> rows = new LinkedHashMap<>();

        @Override
        public SettlementItem save(SettlementItem item) {
            rows.put(item.sellerOrderId(), item);
            return item;
        }

        @Override
        public boolean existsBySellerOrderId(UUID sellerOrderId) {
            return rows.containsKey(sellerOrderId);
        }

        @Override
        public List<SettlementItem> findUnsettled(UUID sellerId, Instant periodStart) {
            return rows.values().stream()
                    .filter(
                            i ->
                                    !i.isSettled()
                                            && i.sellerId().equals(sellerId)
                                            && i.periodStart().equals(periodStart))
                    .toList();
        }

        @Override
        public List<SellerPeriod> findClosablePeriods(Instant closedBy, int limit) {
            return rows.values().stream()
                    .filter(i -> !i.isSettled() && !i.periodEnd().isAfter(closedBy))
                    .map(i -> new SellerPeriod(i.sellerId(), i.periodStart(), i.periodEnd()))
                    .distinct()
                    .sorted(
                            Comparator.comparing(SellerPeriod::periodStart)
                                    .thenComparing(SellerPeriod::sellerId))
                    .limit(limit)
                    .toList();
        }

        Collection<SettlementItem> all() {
            return rows.values();
        }
    }

    static final class Settlements implements SettlementRepository {
        final Map<UUID, Settlement> rows = new LinkedHashMap<>();

        @Override
        public Settlement save(Settlement settlement) {
            rows.put(settlement.id(), settlement);
            return settlement;
        }

        @Override
        public Optional<Settlement> findForUpdateById(UUID settlementId) {
            return Optional.ofNullable(rows.get(settlementId));
        }

        @Override
        public boolean existsBySellerIdAndPeriodStart(UUID sellerId, Instant periodStart) {
            return rows.values().stream()
                    .anyMatch(
                            s ->
                                    s.sellerId().equals(sellerId)
                                            && s.periodStart().equals(periodStart));
        }
    }

    static final class RecordingSellerLock implements SellerLock {
        final List<UUID> locked = new ArrayList<>();

        @Override
        public void lock(UUID sellerId) {
            locked.add(sellerId);
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

        record Appended(String aggregateType, String aggregateId, String type, Object payload) {}
    }

    static final class RecordingAuditLog implements AuditLog {
        final List<AuditEntry> entries = new ArrayList<>();

        @Override
        public void record(AuditEntry entry) {
            entries.add(entry);
        }
    }
}
