package com.openmarket.fulfillment.ui.web;

import org.jspecify.annotations.Nullable;

/**
 * 반품·교환 승인.
 *
 * @param restock 반품의 재입고 여부. 반품이면 필수, 교환이면 넣지 않는다
 */
public record ApproveClaimRequest(@Nullable Boolean restock) {}
