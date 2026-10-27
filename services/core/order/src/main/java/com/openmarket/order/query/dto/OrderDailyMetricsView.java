package com.openmarket.order.query.dto;

import java.time.LocalDate;

/**
 * 하루(KST)의 주문 지표.
 *
 * @param orderCount 그날 주문한 주문 수. 상태와 상관없다(만료·취소 포함)
 * @param paidOrderCount 그날 결제된 주문 수
 * @param gmv 그날 결제된 주문의 결제 금액(쿠폰 할인 뒤) 합(원). 뒤에 취소·환불돼도 빼지 않는다
 */
public record OrderDailyMetricsView(
        LocalDate date, long orderCount, long paidOrderCount, long gmv) {}
