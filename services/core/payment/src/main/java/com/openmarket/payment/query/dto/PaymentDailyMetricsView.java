package com.openmarket.payment.query.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * 하루(KST)의 결제 승인 시도 결과.
 *
 * @param approvedCount 승인된 시도 수(응답이나 뒤 조회로 승인이 정해짐)
 * @param rejectedCount 거절된 시도 수
 * @param undecidedCount 아직 정해지지 않은 시도 수
 * @param failureRate 거절 ÷ (승인 + 거절), 소수 넷째 자리. 정해진 시도가 없으면 비어 있다
 */
public record PaymentDailyMetricsView(
        LocalDate date,
        long approvedCount,
        long rejectedCount,
        long undecidedCount,
        @Nullable BigDecimal failureRate) {}
