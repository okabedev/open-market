package com.openmarket.member.query.dto;

import java.util.UUID;

/** 판매자 ID와 상호. 관리자 쿠폰·정산 목록이 판매자 ID 대신 상호를 보인다. */
public record SellerStoreNameView(UUID sellerId, String storeName) {}
