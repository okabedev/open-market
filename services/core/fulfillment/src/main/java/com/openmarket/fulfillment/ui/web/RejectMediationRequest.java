package com.openmarket.fulfillment.ui.web;

import jakarta.validation.constraints.NotNull;

/**
 * 중재 거부 확정.
 *
 * @param reason 거부 확정 사유 1~500자. 구매자에게 보인다
 */
public record RejectMediationRequest(@NotNull String reason) {}
