package com.openmarket.order.ui.web;

import jakarta.validation.constraints.NotNull;

/**
 * 장바구니 수량 바꾸기.
 *
 * @param quantity 1~99
 */
public record ChangeCartItemQuantityRequest(@NotNull Integer quantity) {}
