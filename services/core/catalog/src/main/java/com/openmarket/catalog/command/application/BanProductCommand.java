package com.openmarket.catalog.command.application;

import java.util.UUID;

/**
 * 관리자가 상품을 제재한다(CAT-5).
 *
 * @param reason 제재 사유. 판매자에게 보인다
 */
public record BanProductCommand(UUID adminId, UUID productId, String reason) {}
