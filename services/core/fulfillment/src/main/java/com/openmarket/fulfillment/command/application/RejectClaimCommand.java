package com.openmarket.fulfillment.command.application;

import java.util.UUID;

/**
 * 판매자가 반품·교환을 거부한다(CLM-3).
 *
 * @param reason 거부 사유. 앞뒤 공백을 지우고 1~500자
 */
public record RejectClaimCommand(UUID sellerId, UUID claimId, String reason) {}
