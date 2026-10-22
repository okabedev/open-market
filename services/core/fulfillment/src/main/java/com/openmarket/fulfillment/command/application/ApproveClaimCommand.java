package com.openmarket.fulfillment.command.application;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 판매자가 반품·교환을 승인한다(CLM-3).
 *
 * @param restock 반품의 재입고 여부. 반품이면 필수, 교환이면 무시한다
 */
public record ApproveClaimCommand(UUID sellerId, UUID claimId, @Nullable Boolean restock) {}
