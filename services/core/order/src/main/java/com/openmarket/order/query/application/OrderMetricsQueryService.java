package com.openmarket.order.query.application;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;
import com.openmarket.order.query.dao.OrderMetricsQueryDao;
import com.openmarket.order.query.dto.OrderDailyMetricsView;
import com.openmarket.order.query.dto.OrderMetricsView;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** 관리자 주문 지표(ADM-4, docs/architecture/order.md "운영 지표"). 업무 DB에서 그때그때 센다(ADR 0023). */
@Service
public class OrderMetricsQueryService {

    /** 한 번에 볼 수 있는 최대 일수. */
    static final int MAX_DAYS = 31;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final OrderMetricsQueryDao dao;

    public OrderMetricsQueryService(OrderMetricsQueryDao dao) {
        this.dao = dao;
    }

    /** 날짜(KST, {@code from}~{@code to} 포함)별 주문 지표. 주문이 없는 날도 0으로 준다. */
    public OrderMetricsView metrics(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BusinessException(
                    ErrorType.INVALID, "ORDER_INVALID_INPUT", "from은 to보다 늦을 수 없습니다.");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw new BusinessException(
                    ErrorType.INVALID,
                    "ORDER_METRICS_PERIOD_TOO_LONG",
                    "기간은 " + MAX_DAYS + "일까지입니다.");
        }
        var start = from.atStartOfDay(KST).toInstant();
        var end = to.plusDays(1).atStartOfDay(KST).toInstant();
        Map<LocalDate, Long> placed = dao.placedCounts(start, end);
        Map<LocalDate, OrderMetricsQueryDao.Paid> paid = dao.paidTotals(start, end);
        List<OrderDailyMetricsView> days = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            OrderMetricsQueryDao.Paid totals =
                    paid.getOrDefault(day, new OrderMetricsQueryDao.Paid(0, 0));
            days.add(
                    new OrderDailyMetricsView(
                            day, placed.getOrDefault(day, 0L), totals.orders(), totals.gmv()));
        }
        return new OrderMetricsView(from, to, List.copyOf(days));
    }
}
