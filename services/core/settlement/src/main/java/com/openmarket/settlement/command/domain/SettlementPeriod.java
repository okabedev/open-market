package com.openmarket.settlement.command.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * 정산 기간(docs/glossary.md "정산 기간"). 기준 시각에서 길이씩 나눈 구간 {@code [start, end)}이다. 규칙은
 * docs/architecture/settlement.md "정산 기간".
 */
public record SettlementPeriod(Instant start, Instant end) {

    public SettlementPeriod {
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("정산 기간은 끝이 시작보다 뒤다");
        }
    }

    /** 시각이 들어가는 기간. 경계 시각은 다음 기간이고, 기준 시각 전의 시각은 그 앞 기간이다. */
    public static SettlementPeriod of(Instant at, Instant anchor, Duration length) {
        if (length.isNegative() || length.isZero()) {
            throw new IllegalArgumentException("정산 기간 길이는 0보다 크다: " + length);
        }
        long lengthMillis = length.toMillis();
        long index = Math.floorDiv(Duration.between(anchor, at).toMillis(), lengthMillis);
        Instant start = anchor.plusMillis(index * lengthMillis);
        return new SettlementPeriod(start, start.plusMillis(lengthMillis));
    }

    /** 바로 다음 기간. */
    public SettlementPeriod next() {
        return new SettlementPeriod(end, end.plus(Duration.between(start, end)));
    }
}
