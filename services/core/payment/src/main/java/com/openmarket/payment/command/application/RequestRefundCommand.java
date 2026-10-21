package com.openmarket.payment.command.application;

import java.util.UUID;

/**
 * 승인된 클레임의 환불을 요청한다({@code ClaimApproved}, PAY-2).
 *
 * @param amount 환불 금액(원). fulfillment가 정했다. 0일 수 있다
 */
public record RequestRefundCommand(UUID claimId, UUID orderId, UUID sellerOrderId, long amount) {}
