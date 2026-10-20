package com.openmarket.order.ui.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 주문 생성. 규칙은 docs/architecture/order.md "주문 생성".
 *
 * @param lines 1~50줄. 같은 SKU는 한 줄만
 * @param shipping member 배송지 목록에서 고른 배송지
 */
public record PlaceOrderRequest(
        @NotNull List<@Valid @NotNull Line> lines, @Valid @NotNull Shipping shipping) {

    /**
     * 주문할 SKU 하나.
     *
     * @param quantity 1~99
     * @param expectedPrice 주문서 화면이 보여 준 개당 가격(원). 지금 가격과 다르면 409 {@code ORDER_PRICE_CHANGED}
     */
    public record Line(
            @NotNull UUID skuId, @NotNull Integer quantity, @NotNull Long expectedPrice) {}

    /**
     * 배송지.
     *
     * @param recipientPhone {@code 010-1234-5678} 형식
     * @param zipCode 숫자 5자리
     */
    public record Shipping(
            @Nullable String recipientName,
            @Nullable String recipientPhone,
            @Nullable String zipCode,
            @Nullable String baseAddress,
            @Nullable String detailAddress) {

        /** 개인정보를 남기지 않는다(NFR-7). */
        @Override
        public String toString() {
            return "Shipping[***]";
        }
    }
}
