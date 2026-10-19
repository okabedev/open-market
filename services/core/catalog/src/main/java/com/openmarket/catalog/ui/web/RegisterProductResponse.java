package com.openmarket.catalog.ui.web;

import java.util.UUID;

/** 등록한 상품. 상태는 {@code DRAFT}다. */
public record RegisterProductResponse(UUID productId) {}
