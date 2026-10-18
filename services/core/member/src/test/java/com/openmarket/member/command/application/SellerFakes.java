package com.openmarket.member.command.application;

import com.openmarket.common.core.audit.AuditEntry;
import com.openmarket.common.core.audit.AuditLog;
import com.openmarket.common.messaging.outbox.Outbox;
import com.openmarket.member.command.domain.Seller;
import com.openmarket.member.command.domain.SellerApplication;
import com.openmarket.member.command.domain.SellerApplicationRepository;
import com.openmarket.member.command.domain.SellerApplicationStatus;
import com.openmarket.member.command.domain.SellerRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** 입점·판매자 유스케이스 테스트용 가짜 구현. */
final class SellerFakes {

    private SellerFakes() {}

    static final class Applications implements SellerApplicationRepository {
        final Map<UUID, SellerApplication> rows = new HashMap<>();

        @Override
        public boolean existsByMemberIdAndStatus(UUID memberId, SellerApplicationStatus status) {
            return rows.values().stream()
                    .anyMatch(a -> a.memberId().equals(memberId) && a.status() == status);
        }

        @Override
        public Optional<SellerApplication> findForUpdateById(UUID id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public SellerApplication save(SellerApplication application) {
            rows.put(application.id(), application);
            return application;
        }
    }

    static final class Sellers implements SellerRepository {
        final Map<UUID, Seller> rows = new HashMap<>();

        @Override
        public boolean existsById(UUID id) {
            return rows.containsKey(id);
        }

        @Override
        public Optional<Seller> findForUpdateById(UUID id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public Seller save(Seller seller) {
            rows.put(seller.id(), seller);
            return seller;
        }
    }

    record Appended(String topic, String key, String type, Object payload) {}

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

    static final class RecordingAuditLog implements AuditLog {
        final List<AuditEntry> entries = new ArrayList<>();

        @Override
        public void record(AuditEntry entry) {
            entries.add(entry);
        }

        List<String> actions() {
            return entries.stream().map(AuditEntry::action).toList();
        }
    }
}
