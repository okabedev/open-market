package com.openmarket.fulfillment.ui.web;

import jakarta.validation.constraints.NotNull;

/**
 * 반품·교환 거부.
 *
 * @param reason 거부 사유 1~500자. 구매자에게 보인다
 */
public record RejectClaimRequest(@NotNull String reason) {}
