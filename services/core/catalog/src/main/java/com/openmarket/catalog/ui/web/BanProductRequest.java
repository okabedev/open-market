package com.openmarket.catalog.ui.web;

import jakarta.validation.constraints.NotNull;

/**
 * 상품 제재.
 *
 * @param reason 제재 사유 1~500자. 판매자에게 보이고 감사 로그에 남는다
 */
public record BanProductRequest(@NotNull String reason) {}
