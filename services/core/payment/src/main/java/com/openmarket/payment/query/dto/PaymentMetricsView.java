package com.openmarket.payment.query.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 관리자 결제 지표(docs/architecture/payment.md "운영 지표"). 기간의 날짜(KST)마다 한 줄이고, 시도가 없는 날도 0으로 들어 있다.
 *
 * @param from 기간 첫날(포함)
 * @param to 기간 마지막 날(포함)
 */
public record PaymentMetricsView(
        LocalDate from, LocalDate to, List<PaymentDailyMetricsView> days) {}
