package com.openmarket.promotion.command.application;

import com.openmarket.common.core.audit.AuditEntry;
import com.openmarket.common.core.audit.AuditLog;
import com.openmarket.promotion.command.domain.Coupon;
import com.openmarket.promotion.command.domain.CouponRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** promotion 유스케이스 테스트용 가짜 구현. */
final class PromotionFakes {

    private PromotionFakes() {}

    static final class Coupons implements CouponRepository {
        final Map<UUID, Coupon> rows = new LinkedHashMap<>();

        @Override
        public Coupon save(Coupon coupon) {
            rows.put(coupon.id(), coupon);
            return coupon;
        }

        @Override
        public Optional<Coupon> findForUpdateById(UUID id) {
            return Optional.ofNullable(rows.get(id));
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
