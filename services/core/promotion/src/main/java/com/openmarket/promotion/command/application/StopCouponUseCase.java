package com.openmarket.promotion.command.application;

import com.openmarket.common.core.audit.AuditEntry;
import com.openmarket.common.core.audit.AuditLog;
import com.openmarket.promotion.command.domain.Coupon;
import com.openmarket.promotion.command.domain.CouponNotFoundException;
import com.openmarket.promotion.command.domain.CouponRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 쿠폰 발급을 멈춘다. 관리자는 모든 쿠폰을, 판매자는 자기 쿠폰만 멈춘다. 다른 판매자의 쿠폰과 플랫폼 쿠폰은 판매자에게 없는 쿠폰이다. 관리자가 멈추면 감사
 * 로그(NFR-8)를 남긴다. 이미 멈췄으면 아무것도 하지 않는다.
 */
@Service
public class StopCouponUseCase {

    static final String AUDIT_TARGET = "COUPON";

    private final CouponRepository coupons;
    private final AuditLog auditLog;
    private final Clock clock;

    public StopCouponUseCase(CouponRepository coupons, AuditLog auditLog, Clock clock) {
        this.coupons = coupons;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Transactional
    public void execute(StopCouponCommand command) {
        UUID sellerId = command.sellerId();
        Coupon coupon =
                coupons.findForUpdateById(command.couponId())
                        .filter(c -> sellerId == null || c.isOwnedBy(sellerId))
                        .orElseThrow(CouponNotFoundException::new);
        if (!coupon.stop(clock.instant())) {
            return;
        }
        coupons.save(coupon);
        if (sellerId == null) {
            auditLog.record(
                    new AuditEntry(
                            command.actorId().toString(),
                            "COUPON_STOPPED",
                            AUDIT_TARGET,
                            coupon.id().toString(),
                            null));
        }
    }
}
