package com.openmarket.order.query.dao;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 날짜(KST)별 주문 지표 집계. 날짜로 묶는 집계라 QueryDSL 대신 SQL로 쓴다(시간대 변환과 {@code GROUP BY}가 SQL에서 분명하다). 주문 수는
 * {@code placed_at}, 결제된 주문 수와 GMV는 {@code paid_at} 인덱스로 찾는다.
 */
@Repository
public class OrderMetricsQueryDao {

    private final JdbcClient jdbc;

    public OrderMetricsQueryDao(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 날짜별 주문 수. 주문이 없는 날은 들어 있지 않다. */
    public Map<LocalDate, Long> placedCounts(Instant from, Instant to) {
        Map<LocalDate, Long> counts = new HashMap<>();
        jdbc.sql(
                        """
                        SELECT (placed_at AT TIME ZONE 'Asia/Seoul')::date AS day, count(*) AS orders
                        FROM orders WHERE placed_at >= :from AND placed_at < :to
                        GROUP BY 1
                        """)
                .param("from", Timestamp.from(from))
                .param("to", Timestamp.from(to))
                .query(
                        rs -> {
                            counts.put(rs.getObject("day", LocalDate.class), rs.getLong("orders"));
                        });
        return counts;
    }

    /** 날짜별 결제된 주문 수와 결제 금액 합. 결제가 없는 날은 들어 있지 않다. */
    public Map<LocalDate, Paid> paidTotals(Instant from, Instant to) {
        Map<LocalDate, Paid> totals = new HashMap<>();
        jdbc.sql(
                        """
                        SELECT (paid_at AT TIME ZONE 'Asia/Seoul')::date AS day,
                               count(*) AS orders, sum(amount) AS gmv
                        FROM orders WHERE paid_at >= :from AND paid_at < :to
                        GROUP BY 1
                        """)
                .param("from", Timestamp.from(from))
                .param("to", Timestamp.from(to))
                .query(
                        rs -> {
                            totals.put(
                                    rs.getObject("day", LocalDate.class),
                                    new Paid(rs.getLong("orders"), rs.getLong("gmv")));
                        });
        return totals;
    }

    /** 하루의 결제된 주문 수와 결제 금액 합(원). */
    public record Paid(long orders, long gmv) {}
}
