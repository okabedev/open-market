package com.openmarket.order.query.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 관리자 주문 지표(docs/architecture/order.md "운영 지표"). 기간의 날짜(KST)마다 한 줄이고, 주문이 없는 날도 0으로 들어 있다.
 *
 * @param from 기간 첫날(포함)
 * @param to 기간 마지막 날(포함)
 */
public record OrderMetricsView(LocalDate from, LocalDate to, List<OrderDailyMetricsView> days) {}
