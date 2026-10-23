package com.openmarket.promotion.command.application;

import com.openmarket.common.core.audit.AuditEntry;
import com.openmarket.common.core.audit.AuditLog;
import com.openmarket.promotion.command.domain.Coupon;
import com.openmarket.promotion.command.domain.CouponRepository;
import com.openmarket.promotion.command.domain.CouponTerms;
import com.openmarket.promotion.command.domain.DiscountType;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 쿠폰 정책을 만든다(PRM-1). 판매자 ID가 있으면 판매자 쿠폰, 없으면 플랫폼 쿠폰이고 관리자 작업이라 감사 로그(NFR-8)를 남긴다. 중복 생성은 {@code
 * Idempotency-Key}가 막는다.
 */
@Service
public class CreateCouponUseCase {

    private final CouponRepository coupons;
    private final AuditLog auditLog;
    private final Clock clock;

    public CreateCouponUseCase(CouponRepository coupons, AuditLog auditLog, Clock clock) {
        this.coupons = coupons;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Transactional
    public UUID execute(CreateCouponCommand command) {
        CouponTerms terms =
                new CouponTerms(
                        command.name(),
                        DiscountType.valueOf(command.discountType().name()),
                        command.discountValue(),
                        command.maxDiscount(),
                        command.minOrderAmount(),
                        command.quantity(),
                        command.issueStartAt(),
                        command.issueEndAt(),
                        command.validDays());
        Instant now = clock.instant();
        UUID sellerId = command.sellerId();
        Coupon coupon =
                sellerId == null
                        ? Coupon.platform(terms, now)
                        : Coupon.seller(sellerId, terms, now);
        coupons.save(coupon);
        if (sellerId == null) {
            auditLog.record(
                    new AuditEntry(
                            command.actorId().toString(),
                            "COUPON_CREATED",
                            StopCouponUseCase.AUDIT_TARGET,
                            coupon.id().toString(),
                            null));
        }
        return coupon.id();
    }
}
