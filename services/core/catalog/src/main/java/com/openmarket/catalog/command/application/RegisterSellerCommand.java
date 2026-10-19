package com.openmarket.catalog.command.application;

import java.util.UUID;

/** 입점 승인({@code SellerApproved})으로 판매자 사본을 만든다. */
public record RegisterSellerCommand(UUID sellerId, String storeName) {}
