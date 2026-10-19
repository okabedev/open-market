package com.openmarket.catalog.command.application;

import java.util.UUID;

/** 판매자가 상품 판매를 멈추거나 다시 연다. */
public record ChangeSaleStatusCommand(UUID sellerId, UUID productId) {}
