package com.openmarket.fulfillment.command.application;

import java.util.UUID;

/** 구매자가 거부된 반품·교환의 중재를 요청한다(CLM-3). */
public record RequestMediationCommand(UUID buyerId, UUID claimId) {}
