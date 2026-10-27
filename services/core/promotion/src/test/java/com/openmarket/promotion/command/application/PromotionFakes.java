package com.openmarket.promotion.command.application;

import com.openmarket.common.core.audit.AuditEntry;
import com.openmarket.common.core.audit.AuditLog;
import com.openmarket.common.messaging.outbox.Outbox;
import com.openmarket.promotion.command.domain.Coupon;
import com.openmarket.promotion.command.domain.CouponRepository;
import com.openmarket.promotion.command.domain.CouponStatus;
import com.openmarket.promotion.command.domain.IssuedCoupon;
import com.openmarket.promotion.command.domain.IssuedCouponRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
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

        /** DB에서만 중지된 쿠폰. 발급 조회가 짧게 캐시한 쿠폰이 아직 발급 중인 상황을 흉내 낸다. */
        final java.util.Set<UUID> stoppedInDb = new java.util.HashSet<>();

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
        public List<Coupon> findAllByIdIn(Collection<UUID> ids) {
            return ids.stream().map(rows::get).filter(java.util.Objects::nonNull).toList();
        }

        @Override
        public Optional<Coupon> findForUpdateById(UUID id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public Optional<CouponStatus> findStatusById(UUID id) {
            if (stoppedInDb.contains(id)) {
                return Optional.of(CouponStatus.STOPPED);
            }
            return findById(id).map(Coupon::status);
        }

        @Override
        public boolean incrementIssuedCount(UUID id, Instant now) {
            Coupon coupon = rows.get(id);
            int count = issuedCount(id);
            if (coupon == null
                    || stoppedInDb.contains(id)
                    || !coupon.isIssuableAt(now)
                    || count >= coupon.terms().quantity()) {
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
        public IssuedCoupon save(IssuedCoupon issued) {
            rows.put(issued.id(), issued);
            return issued;
        }

        @Override
        public List<IssuedCoupon> findAllByIdIn(Collection<UUID> ids) {
            return ids.stream().map(rows::get).filter(java.util.Objects::nonNull).toList();
        }

        @Override
        public List<IssuedCoupon> findForUpdateByIdInOrderById(Collection<UUID> ids) {
            return findAllByIdIn(ids).stream()
                    .sorted(Comparator.comparing(IssuedCoupon::id))
                    .toList();
        }

        @Override
        public List<IssuedCoupon> findForUpdateByOrderIdOrderById(UUID orderId) {
            return rows.values().stream()
                    .filter(i -> i.isBoundTo(orderId))
                    .sorted(Comparator.comparing(IssuedCoupon::id))
                    .toList();
        }

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
