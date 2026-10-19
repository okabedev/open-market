package com.openmarket.catalog.command.application;

import java.util.UUID;

/** 판매자 정지·해제 이벤트({@code SellerSuspended}, {@code SellerReinstated})를 반영한다. */
public record SellerStatusCommand(UUID sellerId) {}
