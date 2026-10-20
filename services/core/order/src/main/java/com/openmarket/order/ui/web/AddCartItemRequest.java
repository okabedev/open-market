package com.openmarket.order.ui.web;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * 장바구니 담기.
 *
 * @param quantity 1~99. 이미 담은 SKU면 더한다
 */
public record AddCartItemRequest(@NotNull UUID skuId, @NotNull Integer quantity) {}
