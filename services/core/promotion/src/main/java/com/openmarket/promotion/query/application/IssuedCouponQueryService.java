package com.openmarket.promotion.query.application;

import com.openmarket.promotion.query.dao.IssuedCouponQueryDao;
import com.openmarket.promotion.query.dto.CursorPageView;
import com.openmarket.promotion.query.dto.IssuedCouponView;
import java.time.Clock;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 구매자 내 쿠폰(PRM-2). 만료는 지금 시각으로 가른다. */
@Service
@Transactional(readOnly = true)
public class IssuedCouponQueryService {

    private final IssuedCouponQueryDao issuedCoupons;
    private final Clock clock;

    public IssuedCouponQueryService(IssuedCouponQueryDao issuedCoupons, Clock clock) {
        this.issuedCoupons = issuedCoupons;
        this.clock = clock;
    }

    /**
     * 내 쿠폰. 최근 발급 순.
     *
     * @param usableOnly true면 지금 쓸 수 있는 쿠폰만(주문서)
     */
    public CursorPageView<IssuedCouponView> mine(
            UUID memberId, boolean usableOnly, @Nullable String cursor, int size) {
        return issuedCoupons.findMine(memberId, usableOnly, clock.instant(), cursor, size);
    }
}
