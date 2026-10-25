package com.openmarket.settlement.command.application;

import com.openmarket.common.core.audit.AuditEntry;
import com.openmarket.common.core.audit.AuditLog;
import com.openmarket.settlement.command.domain.CommissionRate;
import com.openmarket.settlement.command.domain.CommissionRateRepository;
import com.openmarket.settlement.command.domain.SettlementItem;
import com.openmarket.settlement.command.domain.SettlementItemRepository;
import java.util.ArrayList;
import java.util.Collection;
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

        Collection<SettlementItem> all() {
            return rows.values();
        }
    }

    static final class RecordingAuditLog implements AuditLog {
        final List<AuditEntry> entries = new ArrayList<>();

        @Override
        public void record(AuditEntry entry) {
            entries.add(entry);
        }
    }
}
