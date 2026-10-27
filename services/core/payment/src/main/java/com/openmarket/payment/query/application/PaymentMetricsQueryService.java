package com.openmarket.payment.query.application;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;
import com.openmarket.payment.query.dao.PaymentMetricsQueryDao;
import com.openmarket.payment.query.dto.PaymentDailyMetricsView;
import com.openmarket.payment.query.dto.PaymentMetricsView;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/** 관리자 결제 지표(ADM-4, docs/architecture/payment.md "운영 지표"). 업무 DB(원장)에서 그때그때 센다(ADR 0023). */
@Service
public class PaymentMetricsQueryService {

    /** 한 번에 볼 수 있는 최대 일수. */
    static final int MAX_DAYS = 31;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int RATE_SCALE = 4;

    private final PaymentMetricsQueryDao dao;

    public PaymentMetricsQueryService(PaymentMetricsQueryDao dao) {
        this.dao = dao;
    }

    /** 날짜(KST, {@code from}~{@code to} 포함)별 승인 시도 결과와 실패율. 시도가 없는 날도 0으로 준다. */
    public PaymentMetricsView metrics(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BusinessException(
                    ErrorType.INVALID, "PAYMENT_INVALID_INPUT", "from은 to보다 늦을 수 없습니다.");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw new BusinessException(
                    ErrorType.INVALID,
                    "PAYMENT_METRICS_PERIOD_TOO_LONG",
                    "기간은 " + MAX_DAYS + "일까지입니다.");
        }
        Map<LocalDate, PaymentMetricsQueryDao.Attempts> attempts =
                dao.attempts(
                        from.atStartOfDay(KST).toInstant(),
                        to.plusDays(1).atStartOfDay(KST).toInstant());
        List<PaymentDailyMetricsView> days = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            PaymentMetricsQueryDao.Attempts count =
                    attempts.getOrDefault(day, new PaymentMetricsQueryDao.Attempts(0, 0, 0));
            days.add(
                    new PaymentDailyMetricsView(
                            day,
                            count.approved(),
                            count.rejected(),
                            count.undecided(),
                            failureRate(count.approved(), count.rejected())));
        }
        return new PaymentMetricsView(from, to, List.copyOf(days));
    }

    private static @Nullable BigDecimal failureRate(long approved, long rejected) {
        long decided = approved + rejected;
        return decided == 0
                ? null
                : BigDecimal.valueOf(rejected)
                        .divide(BigDecimal.valueOf(decided), RATE_SCALE, RoundingMode.HALF_UP);
    }
}
