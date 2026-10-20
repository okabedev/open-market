package com.openmarket.order.command.application;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 주문 생성(ORD-2). 장바구니의 일부와 바로 주문이 같은 형식이다. */
public record PlaceOrderCommand(UUID buyerId, List<Line> lines, Shipping shipping) {

    /**
     * 주문할 SKU 하나.
     *
     * @param expectedPrice 주문서 화면이 보여 준 개당 가격(원). catalog의 지금 가격과 비교한다
     */
    public record Line(UUID skuId, int quantity, long expectedPrice) {}

    /** 배송지. BFF가 member 배송지 목록에서 고른 내용을 그대로 보낸다. */
    public record Shipping(
            @Nullable String recipientName,
            @Nullable String recipientPhone,
            @Nullable String zipCode,
            @Nullable String baseAddress,
            @Nullable String detailAddress) {}
}
