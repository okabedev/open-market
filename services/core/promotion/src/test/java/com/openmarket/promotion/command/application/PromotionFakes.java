package com.openmarket.promotion.command.application;

import com.openmarket.common.core.audit.AuditEntry;
import com.openmarket.common.core.audit.AuditLog;
import com.openmarket.promotion.command.domain.Coupon;
import com.openmarket.promotion.command.domain.CouponRepository;
import com.openmarket.promotion.command.domain.CouponStatus;
import com.openmarket.promotion.command.domain.IssuedCoupon;
import com.openmarket.promotion.command.domain.IssuedCouponRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** promotion 유스케이스 테스트용 가짜 구현. */
final class PromotionFakes {

    private PromotionFakes() {}

    static final class Coupons implements CouponRepository {
        final Map<UUID, Coupon> rows = new LinkedHashMap<>();
        final Map<UUID, Integer> issuedCounts = new HashMap<>();

        @Override
        public Coupon save(Coupon coupon) {
            rows.put(coupon.id(), coupon);
            return coupon;
        }

        @Override
        public Optional<Coupon> findById(UUID id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public Optional<Coupon> findForUpdateById(UUID id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public Optional<CouponStatus> findStatusById(UUID id) {
            return findById(id).map(Coupon::status);
        }

        @Override
        public boolean incrementIssuedCount(UUID id, Instant now) {
            Coupon coupon = rows.get(id);
            int count = issuedCount(id);
            if (coupon == null || !coupon.isIssuableAt(now) || count >= coupon.terms().quantity()) {
                return false;
            }
            issuedCounts.put(id, count + 1);
            return true;
        }

        int issuedCount(UUID id) {
            return issuedCounts.getOrDefault(id, 0);
        }
    }

    static final class IssuedCoupons implements IssuedCouponRepository {
        final Map<UUID, IssuedCoupon> rows = new LinkedHashMap<>();
        @Nullable RuntimeException insertFailure;

        @Override
        public boolean insertIfAbsent(IssuedCoupon issued) {
            if (insertFailure != null) {
                throw insertFailure;
            }
            if (findIdByCouponIdAndMemberId(issued.couponId(), issued.memberId()).isPresent()) {
                return false;
            }
            rows.put(issued.id(), issued);
            return true;
        }

        @Override
        public Optional<UUID> findIdByCouponIdAndMemberId(UUID couponId, UUID memberId) {
            return rows.values().stream()
                    .filter(i -> i.couponId().equals(couponId) && i.memberId().equals(memberId))
                    .map(IssuedCoupon::id)
                    .findFirst();
        }

        @Override
        public List<UUID> findMemberIdsByCouponId(UUID couponId) {
            return rows.values().stream()
                    .filter(i -> i.couponId().equals(couponId))
                    .map(IssuedCoupon::memberId)
                    .toList();
        }

        long countByCouponId(UUID couponId) {
            return rows.values().stream().filter(i -> i.couponId().equals(couponId)).count();
        }
    }

    /** Redis 게이트와 같은 규칙의 메모리 구현. {@code down}이면 Redis 장애처럼 503을 던진다. */
    static final class Gate implements CouponIssueGatePort {
        final Map<UUID, Integer> stock = new HashMap<>();
        final Map<UUID, Set<UUID>> issued = new HashMap<>();
        boolean down;
        int loads;

        @Override
        public Claim claim(UUID couponId, UUID memberId, int quantity, Instant keysExpireAt) {
            checkUp();
            Integer count = stock.get(couponId);
            if (count == null) {
                return Claim.NOT_LOADED;
            }
            Set<UUID> members = issued.computeIfAbsent(couponId, id -> new HashSet<>());
            if (members.contains(memberId)) {
                return Claim.ALREADY_CLAIMED;
            }
            if (count >= quantity) {
                return Claim.SOLD_OUT;
            }
            members.add(memberId);
            stock.put(couponId, count + 1);
            return Claim.CLAIMED;
        }

        @Override
        public Claim loadAndClaim(
                UUID couponId,
                Collection<UUID> issuedMemberIds,
                UUID memberId,
                int quantity,
                Instant keysExpireAt) {
            checkUp();
            loads++;
            if (!stock.containsKey(couponId)) {
                issued.computeIfAbsent(couponId, id -> new HashSet<>()).addAll(issuedMemberIds);
                stock.put(couponId, issuedMemberIds.size());
            }
            return claim(couponId, memberId, quantity, keysExpireAt);
        }

        @Override
        public void release(UUID couponId, UUID memberId) {
            issued.getOrDefault(couponId, new HashSet<>()).remove(memberId);
            releaseStock(couponId);
        }

        @Override
        public void releaseStock(UUID couponId) {
            stock.computeIfPresent(couponId, (id, count) -> Math.max(0, count - 1));
        }

        @Override
        public void markSoldOut(UUID couponId, int quantity) {
            stock.computeIfPresent(couponId, (id, count) -> Math.max(count, quantity));
        }

        /** Redis 재시작처럼 키를 지운다. */
        void flush() {
            stock.clear();
            issued.clear();
        }

        private void checkUp() {
            if (down) {
                throw CouponIssueGatePort.unavailable(new IllegalStateException("redis down"));
            }
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
