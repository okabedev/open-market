package com.openmarket.payment.query.dto;

import java.util.UUID;

/**
 * 구매자가 보는 주문의 결제. 결제위젯을 그려도 되는지(결제가 준비됐는지) 화면이 확인한다.
 *
 * @param amount 결제 금액(원). 주문 금액과 같다
 */
public record PaymentView(UUID orderId, PaymentStatus status, long amount) {}
