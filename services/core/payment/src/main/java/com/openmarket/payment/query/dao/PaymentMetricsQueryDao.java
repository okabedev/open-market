package com.openmarket.payment.query.dao;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 날짜(KST)별 승인 시도 결과 집계(docs/architecture/payment.md "운영 지표"). 시도마다 뒤 조회를 찾는 집계라 QueryDSL 대신 SQL로
 * 쓴다.
 *
 * <p>승인 시도는 원장의 {@code CONFIRM} 한 줄이다. 결과가 {@code APPROVED}·{@code REJECTED}면 그대로, {@code
 * PENDING}·{@code UNKNOWN}이면 같은 결제·결제 키의 뒤 {@code QUERY} 중 승인·거절로 정해진 첫 결과를 쓰고, 없으면 미정이다. 시도는
 * {@code (kind, occurred_at)}, 뒤 조회는 {@code (payment_id, occurred_at)} 인덱스로 찾는다.
 */
@Repository
public class PaymentMetricsQueryDao {

    private final JdbcClient jdbc;

    public PaymentMetricsQueryDao(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 날짜별 승인·거절·미정 수. 시도가 없는 날은 들어 있지 않다. */
    public Map<LocalDate, Attempts> attempts(Instant from, Instant to) {
        Map<LocalDate, Attempts> result = new HashMap<>();
        jdbc.sql(
                        """
                        SELECT (c.occurred_at AT TIME ZONE 'Asia/Seoul')::date AS day,
                               count(*) FILTER (WHERE r.outcome = 'APPROVED') AS approved,
                               count(*) FILTER (WHERE r.outcome = 'REJECTED') AS rejected,
                               count(*) FILTER (WHERE r.outcome IS NULL) AS undecided
                        FROM payment_ledger_entry c
                        CROSS JOIN LATERAL (
                            SELECT CASE
                                WHEN c.outcome IN ('APPROVED', 'REJECTED') THEN c.outcome
                                ELSE (SELECT q.outcome FROM payment_ledger_entry q
                                      WHERE q.payment_id = c.payment_id
                                        AND q.payment_key = c.payment_key
                                        AND q.kind = 'QUERY'
                                        AND q.occurred_at >= c.occurred_at
                                        AND q.outcome IN ('APPROVED', 'REJECTED')
                                      ORDER BY q.occurred_at
                                      LIMIT 1)
                            END AS outcome
                        ) r
                        WHERE c.kind = 'CONFIRM' AND c.occurred_at >= :from AND c.occurred_at < :to
                        GROUP BY 1
                        """)
                .param("from", Timestamp.from(from))
                .param("to", Timestamp.from(to))
                .query(
                        rs -> {
                            result.put(
                                    rs.getObject("day", LocalDate.class),
                                    new Attempts(
                                            rs.getLong("approved"),
                                            rs.getLong("rejected"),
                                            rs.getLong("undecided")));
                        });
        return result;
    }

    /** 하루의 승인 시도 결과 수. */
    public record Attempts(long approved, long rejected, long undecided) {}
}
