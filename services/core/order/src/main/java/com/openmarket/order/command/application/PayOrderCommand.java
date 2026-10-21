package com.openmarket.order.command.application;

import java.time.Instant;
import java.util.UUID;

/**
 * 결제가 승인된 주문({@code PaymentApproved}).
 *
 * @param amount 승인된 금액(원)
 * @param approvedAt 토스 승인 시각
 */
public record PayOrderCommand(UUID orderId, long amount, Instant approvedAt) {}
