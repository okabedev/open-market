package com.openmarket.order.query.dto;

/** 하위 주문의 마지막 클레임(docs/architecture/order.md "하위 주문 클레임"). 사유, 환불 금액, 시각은 fulfillment 조회에 있다. */
public record SellerOrderClaimView(ClaimType type, ClaimStatus status) {}
